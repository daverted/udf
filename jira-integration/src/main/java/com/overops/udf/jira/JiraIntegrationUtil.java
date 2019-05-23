package com.overops.udf.jira;

import java.util.List;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.overops.udf.jira.JiraIntegrationFunction.JiraIntegrationInput;
import com.takipi.api.client.ApiClient;
import com.takipi.api.client.data.event.Action;
import com.takipi.api.client.request.event.EventActionsRequest;
import com.takipi.api.client.request.event.EventsRequest;
import com.takipi.api.client.result.event.EventActionsResult;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.result.event.EventsResult;
import com.takipi.api.core.url.UrlClient.Response;
import com.takipi.common.util.CollectionUtil;
import com.takipi.udf.ContextArgs;

public class JiraIntegrationUtil {

	// fetch events with a jira url from the previous int days
	public static JiraEventList fetch(ContextArgs args, JiraIntegrationInput input) {
		ApiClient apiClient = args.apiClient();

		LocalDateTime to = LocalDateTime.now();
		LocalDateTime from = to.minusDays(input.days);

		System.out.println("to: " + to.toInstant(ZoneOffset.UTC).toString());
		System.out.println("from: " + from.toInstant(ZoneOffset.UTC).toString());
		System.out.println("view id: " + args.viewId);

		// get new events within the date range
		EventsRequest eventsRequest = EventsRequest.newBuilder().setServiceId(args.serviceId).setViewId(args.viewId)
				.setFrom(from.toInstant(ZoneOffset.UTC).toString()).setTo(to.toInstant(ZoneOffset.UTC).toString()).build();

		// GET events
		Response<EventsResult> eventsResponse = apiClient.get(eventsRequest);

		// validate API response
		if (eventsResponse.isBadResponse()) {
			System.out.println("Failed getting events");
			throw new IllegalStateException("Failed getting events.");
		}

		// get data
		EventsResult eventsResult = eventsResponse.data;

		// return JiraEventList
		JiraEventList eventList = new JiraEventList(input, args);

		// check for events
		if (CollectionUtil.safeIsEmpty(eventsResult.events)) {
			System.out.println("Found no events from the last " + input.days + " days.");
			return eventList;
		}

		// get list of events
		List<EventResult> events = eventsResult.events;

		// for each event with a Jira issue URL
		for (EventResult event : events) {
			if (event.jira_issue_url != null) {
				String issueId = getJiraIssueId(event.jira_issue_url);
				eventList.addEvent(issueId, event);
			}
		}

		return eventList;
	}

	// get Jira issue ID from Jira issue URL
	private static String getJiraIssueId(String jiraURL) {
		int index = jiraURL.lastIndexOf("/")+1;
		return jiraURL.substring(index);
	}

	// get overops update date from last event action
	public static Instant getEventDate(String eventId, ContextArgs args) {
		ApiClient apiClient = args.apiClient();

		EventActionsRequest eventActions = EventActionsRequest.newBuilder()
			.setServiceId(args.serviceId).setEventId(eventId).build();

		// GET event actions
		Response<EventActionsResult> eventActionsResponse = apiClient.get(eventActions);

		// validate API response
		if (eventActionsResponse.isBadResponse())
			throw new IllegalStateException("Failed getting event actions.");

		// extract data
		EventActionsResult eventActionsResult = eventActionsResponse.data;

		// get list of event actions
		List<Action> actions = eventActionsResult.event_actions;

		Instant eventDate = null;

		// get most recent event action timestamp
		if (actions != null && actions.size() > 0) {
			Action a = actions.get(0);
			eventDate = Instant.parse(a.timestamp);
		}

		// can be null
		return eventDate;
	}

}
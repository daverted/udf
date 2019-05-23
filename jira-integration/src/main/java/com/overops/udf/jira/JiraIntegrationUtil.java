package com.overops.udf.jira;

import java.util.List;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.overops.udf.jira.JiraIntegrationFunction.JiraIntegrationInput;
import com.takipi.api.client.ApiClient;
import com.takipi.api.client.request.event.EventsRequest;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.result.event.EventsResult;
import com.takipi.api.core.url.UrlClient.Response;
import com.takipi.common.util.CollectionUtil;
import com.takipi.udf.ContextArgs;

public class JiraIntegrationUtil {

	// fetch events with a jira url from the previous int days
	public static JiraEventList fetch(ContextArgs args, JiraIntegrationInput input) {
		ApiClient apiClient = args.apiClient();

		Instant to = Instant.now();
		Instant from = to.minus(input.days, ChronoUnit.DAYS);

		System.out.println("to: " + to);
		System.out.println("from: " + from);
		System.out.println("view id: " + args.viewId);

		// get new events within the date range
		EventsRequest eventsRequest = EventsRequest.newBuilder().setServiceId(args.serviceId).setViewId(args.viewId)
				.setFrom(from.toString()).setTo(to.toString()).build();

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

}
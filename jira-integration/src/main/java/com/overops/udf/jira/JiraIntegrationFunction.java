package com.overops.udf.jira;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.common.base.Strings;
import com.google.gson.Gson;

import com.takipi.api.client.ApiClient;
import com.takipi.api.client.data.event.Action;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.request.event.EventActionsRequest;
import com.takipi.api.client.request.event.EventsRequest;
import com.takipi.api.client.result.event.EventActionsResult;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.result.event.EventsResult;
import com.takipi.api.client.util.view.ViewUtil;
import com.takipi.api.core.url.UrlClient.Response;
import com.takipi.common.util.CollectionUtil;
import com.takipi.udf.ContextArgs;
import com.takipi.udf.input.Input;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.JiraRestClientFactory;
import com.atlassian.jira.rest.client.domain.Issue;
import com.atlassian.jira.rest.client.domain.User;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.util.concurrent.Promise;


public class JiraIntegrationFunction {
	public static String validateInput(String rawInput) {
		return getJiraIntegrationInput(rawInput).toString();
	}

	public static void execute(String rawContextArgs, String rawInput) {
		JiraIntegrationInput input = getJiraIntegrationInput(rawInput);

		ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

		System.out.println("execute context: " + rawContextArgs);

		if (!args.validate())
			throw new IllegalArgumentException("Bad context args: " + rawContextArgs);

		if (!args.viewValidate())
			return;

		ApiClient apiClient = args.apiClient();

		// get all events that have occurred in the last {timespan} days
		DateTime to = DateTime.now();
		DateTime from = to.minusDays(input.timespan);

		DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();
		EventsRequest eventsRequest = EventsRequest.newBuilder().setServiceId(args.serviceId).setViewId(args.viewId)
				.setFrom(from.toString(fmt)).setTo(to.toString(fmt)).build();

		// GET events
		Response<EventsResult> eventsResponse = apiClient.get(eventsRequest);

		// validate API response
		if (eventsResponse.isBadResponse())
			throw new IllegalStateException("Failed getting events.");

		// get data
		EventsResult eventsResult = eventsResponse.data;

		// check for events
		if (CollectionUtil.safeIsEmpty(eventsResult.events)) {
			System.out.println("Found no events from the last " + input.timespan + " minutes.");
			return;
		}

		// get list of events
		List<EventResult> events = eventsResult.events;

		// Construct the JRJC client
		System.out.println(String.format("Logging in to %s with username '%s'", input.jiraURL, input.jiraUsername));
		JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
		URI uri;

		try {

			uri = new URI(input.jiraURL);
			JiraRestClient client = factory.createWithBasicHttpAuthentication(uri, input.jiraUsername, input.jiraPassword);

			// Invoke the JRJC Client
			Promise<User> promise = client.getUserClient().getUser(input.jiraUsername);
			User user = promise.claim();

			// Sanity check
			// System.out.println(String.format("Your Jira user's email address is: %s\r\n", user.getEmailAddress()));
			// System.out.println();

			// for each event with a Jira issue URL
			for (EventResult event : events) {
				if (event.jira_issue_url != null) {

					// get Jira issue ID from OO event's Jira issue URL
					String issueId = getJiraIssueId(event.jira_issue_url);

					System.out.print(issueId);
					System.out.print(" >> ");
					System.out.println(event);

					// get issue from Jira
					Issue issue = client.getIssueClient().getIssue(issueId).claim();

					// get Jira issue status
					String issueStatus = issue.getStatus().getName();
					System.out.println("issue status: " + issueStatus);

					// Jira updated date
					DateTime jiraDate = issue.getUpdateDate();

					// OverOps updated date
					DateTime overopsDate = getUpdateDate(args.serviceId, event.id, apiClient);

					// compare dates
					System.out.print("last updated by: ");
					if (jiraDate.isAfter(overopsDate)) {
						// Jira
						System.out.println("Jira");
					} else {
						// OverOps
						System.out.println("OverOps");
					}

					// maybe do more with event actions...

					// TODO compare resolved / hidden / reopened
					// TODO which statuses are required?
					// if different, check dates
					// switch (issueStatus) {
					// 	case input.resolvedStatus: 

					// }

					// is resolved in Jira?
					boolean isJiraResolved = issue.getStatus().getName().equals(input.resolvedStatus);
					System.out.println("is resolved in Jira? " + isJiraResolved);

					// is resolved in OverOps?
					boolean isResolved = event.labels.contains("Resolved");
					System.out.println("is resolved in OverOps? " + isResolved);

					// is hidden in OverOps?
					// is hidden in Jira?


					// make it easier to read output
					System.out.println();
				}

			}

		} catch (Exception e) {
			// e.printStackTrace();
			System.err.println("Caught exception. Check settings and try again.");
			System.exit(1);
		}

		System.out.println("Sync complete.");
		System.exit(0);
	}

	// get Jira issue ID from Jira issue URL
	private static String getJiraIssueId(String jiraURL) {
		int index = jiraURL.lastIndexOf("/")+1;
		return jiraURL.substring(index);
	}

	// get overops update date from last event action
	private static DateTime getUpdateDate(String serviceId, String eventId, ApiClient apiClient) {

		EventActionsRequest eventActions = EventActionsRequest.newBuilder()
			.setServiceId(serviceId).setEventId(eventId).build();

		// GET event actions
		Response<EventActionsResult> eventActionsResponse = apiClient.get(eventActions);

		// validate API response
		if (eventActionsResponse.isBadResponse())
			throw new IllegalStateException("Failed getting event actions.");

		// extract data
		EventActionsResult eventActionsResult = eventActionsResponse.data;

		// get list of event actions
		List<Action> actions = eventActionsResult.event_actions;

		DateTime overopsDate = null;

		// get most recent event action timestamp
		if (actions != null && actions.size() > 0) {
			Action a = actions.get(0);
			overopsDate = new DateTime(a.timestamp);
		} else {
			// if null, use now
			overopsDate = new DateTime();
		}

		return overopsDate;
	}

	private static JiraIntegrationInput getJiraIntegrationInput(String rawInput) {
		System.out.println("rawInput:" + rawInput);

		// params cannot be empty
		if (Strings.isNullOrEmpty(rawInput))
			throw new IllegalArgumentException("Input is empty");

		JiraIntegrationInput input;

		// parse params
		try {
			input = JiraIntegrationInput.of(rawInput);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		// validate timespan
		if (input.timespan <= 0)
			throw new IllegalArgumentException("'timespan' must be positive");

		if (input.jiraURL == null || input.jiraURL.isEmpty())
			throw new IllegalArgumentException("'jiraURL' is not defined");

		if (input.jiraUsername == null || input.jiraUsername.isEmpty())
			throw new IllegalArgumentException("'jiraUsername' is not defined");

		if (input.jiraPassword == null || input.jiraPassword.isEmpty())
			throw new IllegalArgumentException("'jiraPassword' is not defined");


		return input;
	}

	static class JiraIntegrationInput extends Input {
		public int timespan; // in days

		public String jiraURL;
		public String jiraUsername;
		public String jiraPassword;

		public String resolvedStatus;

		private JiraIntegrationInput(String raw) {
			super(raw);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();

			builder.append("Jira Integration (");
			builder.append(timespan);
			builder.append(" days)");

			return builder.toString();
		}

		static JiraIntegrationInput of(String raw) {
			return new JiraIntegrationInput(raw);
		}
	}

	// A sample program on how to programmatically activate
	public static void main(String[] args) {
		if ((args == null) || (args.length < 6))
			throw new IllegalArgumentException("java JiraIntegrationFunction API_URL API_KEY SERVICE_ID JIRA_URL JIRA_USER JIRA_PASS");

		ContextArgs contextArgs = new ContextArgs();

		contextArgs.apiHost = args[0];
		contextArgs.apiKey = args[1];
		contextArgs.serviceId = args[2];

		SummarizedView view = ViewUtil.getServiceViewByName(contextArgs.apiClient(), contextArgs.serviceId, "All Events");

		contextArgs.viewId = view.id;

		System.out.println("current time: " + DateTime.now());
		System.out.println("view id: " + view.id);

		// some test values
		String[] sampleValues = new String[] { 
			"timespan=5",
			"jiraURL=" + args[3],
			"jiraUsername=" + args[4],
			"jiraPassword=" + args[5],
			"resolvedStatus=Done"
		};

		String rawContextArgs = new Gson().toJson(contextArgs);
		JiraIntegrationFunction.execute(rawContextArgs, String.join("\n", sampleValues));
	}
}

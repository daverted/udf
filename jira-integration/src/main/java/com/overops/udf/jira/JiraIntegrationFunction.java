package com.overops.udf.jira;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.JiraRestClientFactory;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.takipi.api.client.ApiClient;
import com.takipi.api.client.request.event.EventsRequest;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.result.event.EventsResult;
import com.takipi.api.core.url.UrlClient.Response;
import com.takipi.common.util.CollectionUtil;
import com.takipi.udf.ContextArgs;
import com.takipi.udf.input.Input;
import com.takipi.udf.util.TestUtil;

public class JiraIntegrationFunction {

	public static String validateInput(String rawInput) {
		JiraIntegrationInput input = getJiraIntegrationInput(rawInput);

		// INTG-203: sync either 'resolution' or 'status' field
		if (!input.resolutionOrStatus.equals("resolution") && !input.resolutionOrStatus.equals("status")) {
			throw new IllegalArgumentException("'resolutionOrStatus' must be 'resolution' or 'status'");
		}

		// INTG-200: at least one (resolved, hidden) is required.
		if (StringUtils.isEmpty(input.resolvedStatus) && StringUtils.isEmpty(input.hiddenStatus)) {
			throw new IllegalArgumentException("'resolvedStatus' or 'hiddenStatus' is required");
		}

		// validate credentials by logging in
		JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();

		URI uri;

		try {
			uri = new URI(input.jiraURL);

			// Construct the JRJC client
			JiraRestClient client = factory.createWithBasicHttpAuthentication(uri, input.jiraUsername, input.jiraToken);

			// Make the client log in by performing a search

			// INTG-200: resolved status must exist in Jira.
			if (!StringUtils.isEmpty(input.resolvedStatus)) {
				client.getSearchClient().searchJql(input.resolutionOrStatus + " = \""+ input.resolvedStatus +"\"", 1, 0).claim();
				System.out.println(">> verified input.resolveStatus");
			}

			// INTG-200: hidden status must exist in Jira.
			if (!StringUtils.isEmpty(input.hiddenStatus)) {
				client.getSearchClient().searchJql(input.resolutionOrStatus + " = \""+ input.hiddenStatus +"\"", 1, 0).claim();
				System.out.println(">> verified input.hiddenStatus");
			}

		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid URL. Check jiraURL and try again");
		// } catch (RestClientException restEx) {

		// 	throw new IllegalArgumentException(restEx.getMessage());
		} catch (Exception e) {
			// invalid credentials results in org.codehaus.jettison.json.JSONException in JiraClient
			// exception message is HTML
			if (e.getMessage().contains("AUTHENTICATED_FAILED")) {
				// failed = invalid username / password
				throw new IllegalArgumentException("Authentication failed.");
			} else if (e.getMessage().contains("AUTHENTICATION_DENIED")) {
				// denied = failed CAPTHCA challenge
				throw new IllegalArgumentException("Authentication denied. Please disable Jira CAPTCHA challenge.");
			} else {
				// log other errors
				System.out.println("---- JIRA UDF VALIDATION EXCEPTION: ----");
				System.out.println(e.getMessage());
				System.out.println("----------------------------------------");

				throw new IllegalArgumentException(e.getMessage());
			}
		}

		return input.toString();
	}

	static JiraIntegrationInput getJiraIntegrationInput(String rawInput) {
		// params cannot be empty
		if (Strings.isNullOrEmpty(rawInput)) {
			throw new IllegalArgumentException("Input is empty");
		}

		JiraIntegrationInput input;

		// parse params
		try {
			input = JiraIntegrationInput.of(rawInput);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		// validate days
		if (input.days <= 0) {
			throw new IllegalArgumentException("'days' must be at least 1 day");
		}

		if (StringUtils.isEmpty(input.jiraURL)) {
			throw new IllegalArgumentException("'jiraURL' is required");
		}

		if (StringUtils.isEmpty(input.jiraUsername)) {
			throw new IllegalArgumentException("'jiraUsername' is required");
		}

		if (StringUtils.isEmpty(input.jiraToken)) {
			throw new IllegalArgumentException("'jiraToken' is required");
		}

		return input;
	}

	public static void execute(String rawContextArgs, String rawInput) {
		JiraIntegrationInput input = getJiraIntegrationInput(rawInput);

		ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

		if (!args.validate()) {
			throw new IllegalArgumentException("Bad context args: " + rawContextArgs);
		}

		System.out.println(String.format("Logging in to %s with username '%s'", input.jiraURL, input.jiraUsername));

		JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();

		URI uri;

		try {
			uri = new URI(input.jiraURL);

			// Construct the JRJC client
			JiraRestClient client = factory.createWithBasicHttpAuthentication(uri, input.jiraUsername, input.jiraToken);

			// fetch events with jira issue URLs
			JiraEventList jiraEvents = fetchJiraEvents(args, input);

			// sync with Jira
			jiraEvents.sync(client);

		} catch (URISyntaxException e) {
			System.out.println("Caught URISyntaxException. Check jiraURL and try again.");
			System.out.println(e.getMessage());
			System.exit(1);
		} catch (Exception e) {
			// invalid credentials results in org.codehaus.jettison.json.JSONException in
			// JiraClient
			System.out.println("Caught Exception from Jira Client.");
			System.out.println(e.getMessage());
			System.exit(1);
		}

		System.exit(0);
	}

	// fetch overops events that have a jira url from the last int days
	private static JiraEventList fetchJiraEvents(ContextArgs args, JiraIntegrationInput input) {
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
		int index = jiraURL.lastIndexOf("/") + 1;
		return jiraURL.substring(index);
	}

	static class JiraIntegrationInput extends Input {
		public int days; // in days

		public String jiraURL;
		public String jiraUsername;
		public String jiraToken;

		public String resolutionOrStatus; // INTG-203
		public String resolvedStatus;
		public String hiddenStatus;

		private JiraIntegrationInput(String raw) {
			super(raw);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();

			builder.append("Sync Jira (");
			builder.append(days);
			builder.append("d)");

			return builder.toString();
		}

		static JiraIntegrationInput of(String raw) {
			return new JiraIntegrationInput(raw);
		}
	}

	// for testing locally
	public static void main(String[] args) {
		Instant start = Instant.now(); // timer

		if ((args == null) || (args.length < 10))
			throw new IllegalArgumentException(
					"java JiraIntegrationFunction API_URL API_KEY SERVICE_ID JIRA_URL JIRA_USER JIRA_PASS "
							+ "DAYS RESOLVED_STATUS HIDDEN_STATUS VIEW_NAME");

		String rawContextArgs = TestUtil.getViewContextArgs(args, args[9]);

		// some test values
		String[] sampleValues = new String[] {
				"jiraURL=" + args[3],
				"jiraUsername=" + args[4],
				"jiraToken=" + args[5],
				"days=" + args[6],	// 14
				"resolvedStatus=" + args[7],	// Resolved
				"hiddenStatus=" + args[8]	// Won't Fix, Closed
		};

		try {
			JiraIntegrationFunction.execute(rawContextArgs, String.join("\n", sampleValues));
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		Instant finish = Instant.now(); // timer
		long timeElapsed = Duration.between(start, finish).toMillis(); // in millis

		System.err.print("Sync complete. Time elapsed: ");
		System.err.print(timeElapsed);
		System.err.println("ms");

		System.exit(0);
	}
}
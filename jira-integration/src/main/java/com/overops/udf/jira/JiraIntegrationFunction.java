package com.overops.udf.jira;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.amazonaws.services.lambda.runtime.Context;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.takipi.api.client.ApiClient;
import com.takipi.api.client.data.event.Action;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.request.event.EventActionsRequest;
import com.takipi.api.client.request.event.EventDeleteRequest;
import com.takipi.api.client.request.event.EventInboxRequest;
import com.takipi.api.client.request.event.EventMarkResolvedRequest;
import com.takipi.api.client.request.event.EventsRequest;
import com.takipi.api.client.result.EmptyResult;
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
import com.atlassian.jira.rest.client.domain.Transition;
import com.atlassian.jira.rest.client.domain.User;
import com.atlassian.jira.rest.client.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.util.concurrent.Promise;

public class JiraIntegrationFunction {

	public static String validateInput(String rawInput) {
		return getJiraIntegrationInput(rawInput).toString();
	}

	public static void execute(String rawContextArgs, String rawInput) {
		JiraIntegrationInput input = getJiraIntegrationInput(rawInput);

		ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

		if (!args.validate()) {
			System.out.println("Bad context args");
			throw new IllegalArgumentException("Bad context args: " + rawContextArgs);
		}

		if (!args.viewValidate()) {
			System.out.println("invalid view");
			return;
		}

		// Construct the JRJC client
		System.out.println(String.format("Logging in to %s with username '%s'", input.jiraURL, input.jiraUsername));
		JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
		URI uri;

		try {

			ArrayList<Long> jiraTimer = new ArrayList<Long>();
			ArrayList<Long> overopsTimer = new ArrayList<Long>();

			Instant start = Instant.now(); // timer

			uri = new URI(input.jiraURL);
			JiraRestClient client = factory.createWithBasicHttpAuthentication(uri, input.jiraUsername, input.jiraPassword);

			// Invoke the JRJC Client
			Promise<User> promise = client.getUserClient().getUser(input.jiraUsername);
			User user = promise.claim();

			Instant finish = Instant.now(); // timer
			long timeElapsed = Duration.between(start, finish).toMillis();  //in millis
			jiraTimer.add(timeElapsed);

			// Sanity check
			System.out.println(String.format("Your Jira user's email address is: %s\r\n", user.getEmailAddress()));
			System.out.println("current time: " + LocalTime.now());

			System.out.println("");

		ApiClient apiClient = args.apiClient();

		// get all events that have occurred in the last {timespan} days
		DateTime to = DateTime.now();
		DateTime from = to.minusDays(input.timespan);

		DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

		EventsRequest eventsRequest = EventsRequest.newBuilder().setServiceId(args.serviceId).setViewId(args.viewId)
			.setFrom(from.toString(fmt)).setTo(to.toString(fmt)).build();

		start = Instant.now(); // timer

		// GET events
		Response<EventsResult> eventsResponse = apiClient.get(eventsRequest);

		finish = Instant.now(); // timer
		timeElapsed = Duration.between(start, finish).toMillis();  //in millis
		overopsTimer.add(timeElapsed);


		// validate API response
		if (eventsResponse.isBadResponse()) {
			System.out.println("Failed getting events");
			throw new IllegalStateException("Failed getting events.");
		}

		// get data
		EventsResult eventsResult = eventsResponse.data;

		// check for events
		if (CollectionUtil.safeIsEmpty(eventsResult.events)) {
			System.out.println("Found no events from the last " + input.timespan + " days.");
			return;
		}

		// get list of events
		List<EventResult> events = eventsResult.events;

		System.out.println(events.size() + " events");

		Set<String> callStackGroupSet = new HashSet<String>();

			// for each event with a Jira issue URL
			for (EventResult event : events) {
				if (event.jira_issue_url != null) {

					// ignore similar events
					if (callStackGroupSet.contains(event.call_stack_group)) {
						System.out.println("IGNORE EVENT: " + event);
						continue;
					} else {
						callStackGroupSet.add(event.call_stack_group);
					}

					// get Jira issue ID from OO event's Jira issue URL
					String issueId = getJiraIssueId(event.jira_issue_url);
					System.out.print(issueId + " >> " + event.toString());

					// get issue from Jira
					start = Instant.now(); // timer

					Issue issue = client.getIssueClient().getIssue(issueId).claim();

					finish = Instant.now(); // timer
					timeElapsed = Duration.between(start, finish).toMillis();  //in millis
					jiraTimer.add(timeElapsed);

					// get Jira issue status
					String issueStatus = issue.getStatus().getName();
					System.out.print(" issue status: " + issueStatus);

					// Jira updated date
					DateTime jiraDate = issue.getUpdateDate();
					System.out.print(" jira updated date: " + jiraDate.toString());

					start = Instant.now();

					// OverOps updated date
					DateTime overopsDate = getUpdateDate(args.serviceId, event.id, apiClient);
					finish = Instant.now(); // timer
					timeElapsed = Duration.between(start, finish).toMillis();  //in millis
					overopsTimer.add(timeElapsed);

					System.out.print(" overops updated date: " + overopsDate.toString());

					// TODO resurfaced = deployment version needs to change <---

					// TODO ensure Jira statuses are unique
					// TODO make sure null, empty string, undefined all work

					boolean isResolved = event.labels.contains("Resolved"); // is resolved in OverOps?
					boolean isHidden = event.labels.contains("Archive"); // is hidden in OverOps?
					boolean isInbox = event.labels.contains("Inbox"); // is in inbox in OverOps?
					boolean isResurfaced = event.labels.contains("Resurfaced"); // is resurfaced in OverOps?

					boolean isResolvedStatus = issueStatus.equals(input.resolvedStatus); // is resolved in Jira?
					boolean isHiddenStatus = issueStatus.equals(input.hiddenStatus); // is hidden in Jira?
					boolean isInboxStatus = issueStatus.equals(input.inboxStatus); // is in inbox in Jira?
					boolean isResurfacedStatus = issueStatus.equals(input.resurfacedStatus); // is resurfaced in Jira?

					// compare dates
					if (overopsDate.isAfter(jiraDate)) {
						// Sync OverOps to Jira
						System.out.print(" Sync OverOps >> Jira");

						start = Instant.now();

						// get possible status transitions
						Iterable<Transition> transitions = client.getIssueClient().getTransitions(issue).claim();

						finish = Instant.now(); // timer
						timeElapsed = Duration.between(start, finish).toMillis();  //in millis
						jiraTimer.add(timeElapsed);

						if (isResolved) {
							// 1. resolve in OO → resolve in Jira
							System.out.print(" Resolved in OverOps");

							// if not resolved, resolve in Jira
							if (!isResolvedStatus) {
								System.out.print(" NOT Resolved in Jira. Resolving...");
								for (Transition transition : transitions) {
									if (transition.getName().equals(input.resolvedStatus)) {
										TransitionInput transitionInput = new TransitionInput(transition.getId());

										start = Instant.now(); // timer

										client.getIssueClient().transition(issue, transitionInput).claim();

										finish = Instant.now(); // timer
										timeElapsed = Duration.between(start, finish).toMillis();  //in millis
										jiraTimer.add(timeElapsed);

										break;
									}
								}
							}

						} else if (isHidden) {
							// 2. hide in OO → hide in Jira
							System.out.print(" Hidden in OverOps");

							// if not hidden, hide in Jira
							if (!isHiddenStatus) {
								System.out.print(" NOT Hidden in Jira. Hiding...");
								for (Transition transition : transitions) {
									if (transition.getName().equals(input.hiddenStatus)) {
										TransitionInput transitionInput = new TransitionInput(transition.getId());

										start = Instant.now(); // timer

										client.getIssueClient().transition(issue, transitionInput).claim();

										finish = Instant.now(); // timer
										timeElapsed = Duration.between(start, finish).toMillis();  //in millis
										jiraTimer.add(timeElapsed);

										break;
									}
								}
							}

						} else if (isResurfaced) {
							// 3. resurfaced in OO → resurfaced in Jira
							System.out.print(" Resurfaced in OverOps");

							if (!isResurfacedStatus) {
								System.out.print(" NOT Resurfaced in Jira. Resurfacing...");
								for (Transition transition : transitions) {
									if (transition.getName().equals(input.resurfacedStatus)) {
										TransitionInput transitionInput = new TransitionInput(transition.getId());

										start = Instant.now();

										client.getIssueClient().transition(issue, transitionInput).claim();

										finish = Instant.now(); // timer
										timeElapsed = Duration.between(start, finish).toMillis();  //in millis
										jiraTimer.add(timeElapsed);

										break;
									}
								}
							}

						} else {
							// 4. anything else, mark "in Inbox" in Jira
							System.out.print(" OTHER in OverOps");

							if (!isInboxStatus) {
								System.out.print(" NOT in Inbox in Jira. Moving to Inbox...");
								for (Transition transition : transitions) {
									if (transition.getName().equals(input.inboxStatus)) {
										TransitionInput transitionInput = new TransitionInput(transition.getId());

										start = Instant.now(); // timer

										client.getIssueClient().transition(issue, transitionInput).claim();

										finish = Instant.now(); // timer
										timeElapsed = Duration.between(start, finish).toMillis();  //in millis
										jiraTimer.add(timeElapsed);

										break;
									}
								}
							}
						}

					} else {
						// Sync Jira to OverOps
						System.out.print(" [Sync Jira >> OverOps] ");

						if (isResolvedStatus) {
							// 1. resolve in Jira → resolve in OO
							System.out.print(" Resolved in Jira");

							// if not resolved, resolve in OverOps
							if (!isResolved) {
								System.out.print(" NOT Resolved in OverOps. Resolving...");
								EventMarkResolvedRequest resolvedRequest = EventMarkResolvedRequest.newBuilder()
										.setServiceId(args.serviceId).setEventId(event.id).setHandleSimilarEvents(true).build();

								start = Instant.now(); // timer

								Response<EmptyResult> resolvedResponse = apiClient.post(resolvedRequest);

								finish = Instant.now(); // timer
								timeElapsed = Duration.between(start, finish).toMillis();  //in millis
								overopsTimer.add(timeElapsed);


								if (resolvedResponse.isBadResponse())
									throw new IllegalStateException("Failed resolving event " + event.id);
							}

						} else if (isHiddenStatus) {
							// 2. hide in Jira → hide in OO
							System.out.print(" Hidden in Jira");

							// if not hidden, hide (delete/trash/archive) in OverOps
							if (!isHidden) {
								System.out.print(" NOT Hidden in OverOps. Hiding...");
								EventDeleteRequest hideRequest = EventDeleteRequest.newBuilder().setServiceId(args.serviceId)
										.setEventId(event.id).setHandleSimilarEvents(true).build();

								start = Instant.now(); // timer

								Response<EmptyResult> hideResponse = apiClient.post(hideRequest);

								finish = Instant.now(); // timer
								timeElapsed = Duration.between(start, finish).toMillis();  //in millis
								overopsTimer.add(timeElapsed);

								if (hideResponse.isBadResponse())
									throw new IllegalStateException("Failed hiding event " + event.id);
							}

						} else {
							// 3. anything else in Jira → move to inbox in OO
							System.out.print(" OTHER in Jira");

							// if not in inbox, move to inbox in OverOps
							if (!isInbox) {
								System.out.print(" NOT in Inbox in OverOps. Moving to Inbox...");
								EventInboxRequest inboxRequest = EventInboxRequest.newBuilder().setServiceId(args.serviceId)
									.setEventId(event.id).setHandleSimilarEvents(true).build();

								start = Instant.now();

								Response<EmptyResult> resolvedResponse = apiClient.post(inboxRequest);

								finish = Instant.now(); // timer
								timeElapsed = Duration.between(start, finish).toMillis();  //in millis
								overopsTimer.add(timeElapsed);

								if (resolvedResponse.isBadResponse())
									throw new IllegalStateException("Failed moving event to inbox " + event.id);
							}

						}

					}

				}

				// make logs easier to read
				System.out.println("");
			}

			// make logs easier to read
			System.out.println("");

			// compute average response times
			double jiraTotal = 0;
			for (int i = 0; i < jiraTimer.size(); i++) {
				jiraTotal += jiraTimer.get(i);
			}
			System.out.println("AVERAGE JIRA RESPONSE TIME: " + (jiraTotal / jiraTimer.size()));
			System.out.println("TOTAL JIRA TRANSACTIONS: " + jiraTimer.size());

			double overopsTotal = 0;
			for (int i = 0; i < overopsTimer.size(); i++) {
				overopsTotal += overopsTimer.get(i);
			}
			System.out.println("AVERAGE OVEROPS RESPONSE TIME: " + (overopsTotal / overopsTimer.size()));
			System.out.println("TOTAL OVEROPS TRANSACTIONS: " + overopsTimer.size());

		} catch (Exception e) {
			System.out.println("Caught exception. Check settings and try again.");
			e.toString();
			System.exit(1);
		}

		System.out.println("Sync complete.");
		System.out.println("");
		System.out.println("");
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
			// if null, use epoch
			overopsDate = new DateTime("1970-01-01T00:00:00");
		}

		return overopsDate;
	}

	private static JiraIntegrationInput getJiraIntegrationInput(String rawInput) {
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
			throw new IllegalArgumentException("'timespan' must be at least 1 day");

		if (input.jiraURL == null || input.jiraURL.isEmpty())
			throw new IllegalArgumentException("'jiraURL' is required");

		if (input.jiraUsername == null || input.jiraUsername.isEmpty())
			throw new IllegalArgumentException("'jiraUsername' is required");

		if (input.jiraPassword == null || input.jiraPassword.isEmpty())
			throw new IllegalArgumentException("'jiraPassword' is required");


		return input;
	}

	static class JiraIntegrationInput extends Input {
		public int timespan; // in days

		public String jiraURL;
		public String jiraUsername;
		public String jiraPassword;

		public String inboxStatus;
		public String resolvedStatus;
		public String hiddenStatus;
		public String resurfacedStatus;

		// public boolean updateJira;---> FALSE BY DEFAULT
    // -----> by default this is a ONE WAY SYNC ONLY: Jira to OverOps, not OverOps to Jira.
    // -----> look into event actions to see if there's user, at a minimum, update the ticket to say "close by OverOps" 
    // for one way sync, Jira is king (ignore date)

		private JiraIntegrationInput(String raw) {
			super(raw);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();

			builder.append("Sync Jira (");
			builder.append(timespan);
			builder.append("d)");

			return builder.toString();
		}

		static JiraIntegrationInput of(String raw) {
			return new JiraIntegrationInput(raw);
		}
	}

	// for testing in aws lambda
	public static void lambdaHandler(String input, Context context) {
		// LambdaLogger logger = context.getLogger();
		System.out.println(">> lambdaExecute() ");
		System.out.println(input);

		ContextArgs contextArgs = new ContextArgs();

		contextArgs.apiHost = "https://api.overops.com/";
		contextArgs.apiKey = "***REMOVED***";
		contextArgs.serviceId = "S38358";

		// Use "Jira UDF" View
		SummarizedView view = ViewUtil.getServiceViewByName(contextArgs.apiClient(), contextArgs.serviceId, "All Events");
		contextArgs.viewId = view.id;

		String rawContextArgs = new Gson().toJson(contextArgs);
		JiraIntegrationFunction.execute(rawContextArgs, input);
	}

	// for testing locally
	public static void main(String[] args) {
		if ((args == null) || (args.length < 6))
			throw new IllegalArgumentException("java JiraIntegrationFunction API_URL API_KEY SERVICE_ID JIRA_URL JIRA_USER JIRA_PASS");

		ContextArgs contextArgs = new ContextArgs();

		contextArgs.apiHost = args[0];
		contextArgs.apiKey = args[1];
		contextArgs.serviceId = args[2];

		// Use "Jira UDF" View
		SummarizedView view = ViewUtil.getServiceViewByName(contextArgs.apiClient(), contextArgs.serviceId, "Jira UDF");

		contextArgs.viewId = view.id;

		System.out.println("current time: " + LocalTime.now());
		System.out.println("view id: " + view.id);

		// some test values
		String[] sampleValues = new String[] { 
			"timespan=14",
			"jiraURL=" + args[3],
			"jiraUsername=" + args[4],
			"jiraPassword=" + args[5],
			"resolvedStatus=Resolved",
			"hiddenStatus=Closed",
			"inboxStatus=To Do",
			"resurfacedStatus=Reopened"
		};

		String rawContextArgs = new Gson().toJson(contextArgs);
		JiraIntegrationFunction.execute(rawContextArgs, String.join("\n", sampleValues));
	}
}

package com.overops.udf.jira;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;

import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.takipi.api.client.ApiClient;
// import com.takipi.api.client.data.event.Action;
import com.takipi.api.client.data.view.SummarizedView;
// import com.takipi.api.client.request.event.EventActionsRequest;
// import com.takipi.api.client.request.event.EventDeleteRequest;
// import com.takipi.api.client.request.event.EventInboxRequest;
// import com.takipi.api.client.request.event.EventMarkResolvedRequest;
import com.takipi.api.client.request.event.EventsRequest;
// import com.takipi.api.client.result.EmptyResult;
// import com.takipi.api.client.result.event.EventActionsResult;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.result.event.EventsResult;
import com.takipi.api.client.util.view.ViewUtil;
import com.takipi.api.core.url.UrlClient.Response;
import com.takipi.common.util.CollectionUtil;
import com.takipi.udf.ContextArgs;
import com.takipi.udf.input.Input;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.JiraRestClientFactory;
// import com.atlassian.jira.rest.client.domain.Issue;
// import com.atlassian.jira.rest.client.domain.Transition;
import com.atlassian.jira.rest.client.domain.User;
// import com.atlassian.jira.rest.client.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.util.concurrent.Promise;

public class JiraIntegrationFunction {
	public static String validateInput(String rawInput) {
		return getJiraIntegrationInput(rawInput).toString();
	}

	public static void execute(String rawContextArgs, String rawInput) {
		Runtime runtime = Runtime.getRuntime();
		String freeMemory = runtime.freeMemory() + ""; // quick convert long to String
	  String totalMemory = runtime.totalMemory() + "";

		JiraIntegrationInput input = getJiraIntegrationInput(rawInput);

		ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

		PostLogger postLogger = new PostLogger(input);

		postLogger.log("current time: " + DateTime.now());
		postLogger.log("execute context: " + rawContextArgs);
		postLogger.log("free memory: " + freeMemory);
		postLogger.log("total memory: " + totalMemory);


		if (!args.validate()) {
			postLogger.log("Bad context args");
			throw new IllegalArgumentException("Bad context args: " + rawContextArgs);
		}

		if (!args.viewValidate()) {
			postLogger.log("invalid view");
			return;
		}

		// Construct the JRJC client
		postLogger.log(String.format("Logging in to %s with username '%s'", input.jiraURL, input.jiraUsername));
		JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
		URI uri;

		try {

			uri = new URI(input.jiraURL);
			JiraRestClient client = factory.createWithBasicHttpAuthentication(uri, input.jiraUsername, input.jiraPassword);

			// Invoke the JRJC Client
			Promise<User> promise = client.getUserClient().getUser(input.jiraUsername);
			User user = promise.claim();

			// Sanity check
			postLogger.log(String.format("Your Jira user's email address is: %s\r\n", user.getEmailAddress()));
			postLogger.log("current time: " + DateTime.now());

			postLogger.log("");

		// // start comment

		// ApiClient apiClient = args.apiClient();

		// // get all events that have occurred in the last {timespan} days
		// DateTime to = DateTime.now();
		// DateTime from = to.minusDays(input.timespan);

		// DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

		// EventsRequest eventsRequest = EventsRequest.newBuilder().setServiceId(args.serviceId).setViewId(args.viewId)
		// 	.setFrom(from.toString(fmt)).setTo(to.toString(fmt)).build();

		// // GET events
		// Response<EventsResult> eventsResponse = apiClient.get(eventsRequest);

		// // validate API response
		// if (eventsResponse.isBadResponse()) {
		// 	postLogger.log("Failed getting events");
		// 	throw new IllegalStateException("Failed getting events.");
		// }

		// // get data
		// EventsResult eventsResult = eventsResponse.data;

		// // check for events
		// if (CollectionUtil.safeIsEmpty(eventsResult.events)) {
		// 	postLogger.log("Found no events from the last " + input.timespan + " days.");
		// 	return;
		// }

		// // get list of events
		// List<EventResult> events = eventsResult.events;

		// postLogger.log(events.size() + " events");
		// // end comment

			// // for each event with a Jira issue URL
			// for (EventResult event : events) {
			// 	if (event.jira_issue_url != null) {

			// 		// get Jira issue ID from OO event's Jira issue URL
			// 		String issueId = getJiraIssueId(event.jira_issue_url);
			// 		postLogger.log(issueId + " >> " + event.toString());

			// 		// get issue from Jira
			// 		Issue issue = client.getIssueClient().getIssue(issueId).claim();

			// 		// get Jira issue status
			// 		String issueStatus = issue.getStatus().getName();
			// 		postLogger.log("issue status: " + issueStatus);

			// 		// Jira updated date
			// 		DateTime jiraDate = issue.getUpdateDate();
			// 		postLogger.log("jira updated date: " + jiraDate.toString());

			// 		// OverOps updated date
			// 		DateTime overopsDate = getUpdateDate(args.serviceId, event.id, apiClient);
			// 		postLogger.log("overops updated date: " + jiraDate.toString());

			// 		// TODO open a jira ticket for an API that lets me query for Jira / open / hidden ... to
			// 		// not time sync
			// 		// TODO resurfaced = deployment version needs to change <---

			// 		// TODO ensure Jira statuses are unique
			// 		// TODO make sure null, empty string, undefined all work

			// 		boolean isResolved = event.labels.contains("Resolved"); // is resolved in OverOps?
			// 		boolean isHidden = event.labels.contains("Archive"); // is hidden in OverOps?
			// 		boolean isInbox = event.labels.contains("Inbox"); // is in inbox in OverOps?
			// 		boolean isResurfaced = event.labels.contains("Resurfaced"); // is resurfaced in OverOps?

			// 		boolean isResolvedStatus = issueStatus.equals(input.resolvedStatus); // is resolved in Jira?
			// 		boolean isHiddenStatus = issueStatus.equals(input.hiddenStatus); // is hidden in Jira?
			// 		boolean isInboxStatus = issueStatus.equals(input.inboxStatus); // is in inbox in Jira?
			// 		boolean isResurfacedStatus = issueStatus.equals(input.resurfacedStatus); // is resurfaced in Jira?

			// 		// compare dates
			// 		if (jiraDate.isAfter(overopsDate)) {
			// 			// Sync Jira to OverOps
			// 			postLogger.log("Sync Jira >> OverOps");

			// 			if (isResolvedStatus) {
			// 				// 1. resolve in Jira → resolve in OO
			// 				postLogger.log("Resolved in Jira");

			// 				// if not resolved, resolve in OverOps
			// 				if (!isResolved) {
			// 					postLogger.log("NOT Resolved in OverOps. Resolving...");
			// 					EventMarkResolvedRequest resolvedRequest = EventMarkResolvedRequest.newBuilder()
			// 							.setServiceId(args.serviceId).setEventId(event.id).build();

			// 					Response<EmptyResult> resolvedResponse = apiClient.post(resolvedRequest);

			// 					if (resolvedResponse.isBadResponse())
			// 						throw new IllegalStateException("Failed resolving event " + event.id);
			// 				}

			// 			} else if (isHiddenStatus) {
			// 				// 2. hide in Jira → hide in OO
			// 				postLogger.log("Hidden in Jira");

			// 				// if not hidden, hide (delete/trash/archive) in OverOps
			// 				if (!isHidden) {
			// 					postLogger.log("NOT Hidden in OverOps. Hiding...");
			// 					EventDeleteRequest hideRequest = EventDeleteRequest.newBuilder().setServiceId(args.serviceId)
			// 							.setEventId(event.id).build();

			// 					Response<EmptyResult> hideResponse = apiClient.post(hideRequest);

			// 					if (hideResponse.isBadResponse())
			// 						throw new IllegalStateException("Failed hiding event " + event.id);
			// 				}

			// 			} else {
			// 				// 3. anything else in Jira → move to inbox in OO
			// 				postLogger.log("OTHER in Jira");

			// 				// if not in inbox, move to inbox in OverOps
			// 				if (!isInbox) {
			// 					postLogger.log("NOT in Inbox in OverOps. Moving to Inbox...");
			// 					EventInboxRequest inboxRequest = EventInboxRequest.newBuilder().setServiceId(args.serviceId)
			// 							.setEventId(event.id).build();

			// 					Response<EmptyResult> resolvedResponse = apiClient.post(inboxRequest);

			// 					if (resolvedResponse.isBadResponse())
			// 						throw new IllegalStateException("Failed moving event to inbox " + event.id);
			// 				}

			// 			}

			// 		} else {
			// 			// Sync OverOps to Jira
			// 			postLogger.log("Sync OverOps >> Jira");

			// 			// get possible status transitions
			// 			Iterable<Transition> transitions = client.getIssueClient().getTransitions(issue).claim();

			// 			if (isResolved) {
			// 				// 1. resolve in OO → resolve in Jira
			// 				postLogger.log("Resolved in OverOps");

			// 				// if not resolved, resolve in Jira
			// 				if (!isResolvedStatus) {
			// 					postLogger.log("NOT Resolved in Jira. Resolving...");
			// 					for (Transition transition : transitions) {
			// 						if (transition.getName().equals(input.resolvedStatus)) {
			// 							TransitionInput transitionInput = new TransitionInput(transition.getId());
			// 							client.getIssueClient().transition(issue, transitionInput).claim();
			// 							break;
			// 						}
			// 					}
			// 				}

			// 			} else if (isHidden) {
			// 				// 2. hide in OO → hide in Jira
			// 				postLogger.log("Hidden in OverOps");

			// 				// if not hidden, hide in Jira
			// 				if (!isHiddenStatus) {
			// 					postLogger.log("NOT Hidden in Jira. Hiding...");
			// 					for (Transition transition : transitions) {
			// 						if (transition.getName().equals(input.hiddenStatus)) {
			// 							TransitionInput transitionInput = new TransitionInput(transition.getId());
			// 							client.getIssueClient().transition(issue, transitionInput).claim();
			// 							break;
			// 						}
			// 					}
			// 				}

			// 			} else if (isResurfaced) {
			// 				// 3. resurfaced in OO → resurfaced in Jira
			// 				postLogger.log("Resurfaced in OverOps");

			// 				if (!isResurfacedStatus) {
			// 					postLogger.log("NOT Resurfaced in Jira. Resurfacing...");
			// 					for (Transition transition : transitions) {
			// 						if (transition.getName().equals(input.resurfacedStatus)) {
			// 							TransitionInput transitionInput = new TransitionInput(transition.getId());
			// 							client.getIssueClient().transition(issue, transitionInput).claim();
			// 							break;
			// 						}
			// 					}
			// 				}

			// 			} else {
			// 				// 4. anything else, mark "in Inbox" in Jira
			// 				postLogger.log("OTHER in OverOps");

			// 				if (!isInboxStatus) {
			// 					postLogger.log("NOT in Inbox in Jira. Moving to Inbox...");
			// 					for (Transition transition : transitions) {
			// 						if (transition.getName().equals(input.inboxStatus)) {
			// 							TransitionInput transitionInput = new TransitionInput(transition.getId());
			// 							client.getIssueClient().transition(issue, transitionInput).claim();
			// 							break;
			// 						}
			// 					}
			// 				}
			// 			}

			// 		}

			// 	}

			// 	// make logs easier to read
			// 	postLogger.log(" ");
			// }

			// // make logs easier to read
			// postLogger.log(" ");

		} catch (Exception e) {
			postLogger.log("Caught exception. Check settings and try again.");

			// postLogger.log(e.toString());

			// https://stackoverflow.com/questions/1149703/how-can-i-convert-a-stack-trace-to-a-string
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String sStackTrace = sw.toString(); // stack trace as a string
			postLogger.log(sStackTrace);

			System.exit(1);
		}

		postLogger.log("Sync complete.");
		postLogger.log("");
		postLogger.log("");

		System.exit(0);
	}

	// // get Jira issue ID from Jira issue URL
	// private static String getJiraIssueId(String jiraURL) {
	// 	int index = jiraURL.lastIndexOf("/")+1;
	// 	return jiraURL.substring(index);
	// }

	// get overops update date from last event action
	// private static DateTime getUpdateDate(String serviceId, String eventId, ApiClient apiClient) {

	// 	EventActionsRequest eventActions = EventActionsRequest.newBuilder()
	// 		.setServiceId(serviceId).setEventId(eventId).build();

	// 	// GET event actions
	// 	Response<EventActionsResult> eventActionsResponse = apiClient.get(eventActions);

	// 	// validate API response
	// 	if (eventActionsResponse.isBadResponse())
	// 		throw new IllegalStateException("Failed getting event actions.");

	// 	// extract data
	// 	EventActionsResult eventActionsResult = eventActionsResponse.data;

	// 	// get list of event actions
	// 	List<Action> actions = eventActionsResult.event_actions;

	// 	DateTime overopsDate = null;

	// 	// get most recent event action timestamp
	// 	if (actions != null && actions.size() > 0) {
	// 		Action a = actions.get(0);
	// 		overopsDate = new DateTime(a.timestamp);
	// 	} else {
	// 		// if null, use now
	// 		overopsDate = new DateTime();
	// 	}

	// 	return overopsDate;
	// }

	// simple remote logging to echo server
	static class PostLogger {
		static JiraIntegrationInput input;

		protected PostLogger(JiraIntegrationInput jiraIntegrationInput) {
			input = jiraIntegrationInput;
		}

		// public void log(String message) {
		// 	System.out.println(message);
		// }

		public void log(String message) {
			if (input.echoLogger == null && input.echoLogger.isEmpty()) {
				System.out.println(message);
			}

			HttpClient httpClient = new DefaultHttpClient();

			try {
				HttpPost post = new HttpPost(input.echoLogger);

				// add header
				post.setHeader("User-Agent", "JiraIntegrationUDF");

				post.setEntity(new StringEntity(message, ContentType.create("text/plain")));

				HttpResponse response = httpClient.execute(post);

				// print if remote logging wasn't successful
				if (response.getStatusLine().getStatusCode() != 200) {
					System.out.println("----");
					System.out.println("REMOTE LOGGER STATUS " + response.getStatusLine().getStatusCode());
					System.out.println(message);
					System.out.println("----");
				}

			} catch(Exception e) {
				System.out.println("Caught Exception in remote logger");
				e.printStackTrace();
			} finally {
				httpClient.getConnectionManager().shutdown();
			}

		}

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

		public String echoLogger;

		// TODO
		// public boolean handleSimilarEvents;
		// public boolean updateJira;
		// public boolean updateOverOps;

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

	// for testing
	public static void main(String[] args) {
		if ((args == null) || (args.length < 7))
			throw new IllegalArgumentException("java JiraIntegrationFunction API_URL API_KEY SERVICE_ID JIRA_URL JIRA_USER JIRA_PASS ECHO_LOGGER");

		ContextArgs contextArgs = new ContextArgs();

		contextArgs.apiHost = args[0];
		contextArgs.apiKey = args[1];
		contextArgs.serviceId = args[2];

		// All Events
		SummarizedView view = ViewUtil.getServiceViewByName(contextArgs.apiClient(), contextArgs.serviceId, "Jira UDF");

		contextArgs.viewId = view.id;

		System.out.println("current time: " + DateTime.now());
		System.out.println("view id: " + view.id);

		// some test values
		String[] sampleValues = new String[] { 
			"timespan=14",
			"jiraURL=" + args[3],
			"jiraUsername=" + args[4],
			"jiraPassword=" + args[5],
			"echoLogger=" + args[6],
			"resolvedStatus=Resolved",
			"hiddenStatus=Closed",
			"inboxStatus=To Do",
			"resurfacedStatus=Reopened"
		};

		String rawContextArgs = new Gson().toJson(contextArgs);
		JiraIntegrationFunction.execute(rawContextArgs, String.join("\n", sampleValues));
	}
}

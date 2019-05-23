package com.overops.udf.jira;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;

import com.amazonaws.services.lambda.runtime.Context;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.util.view.ViewUtil;
import com.takipi.udf.ContextArgs;
import com.takipi.udf.input.Input;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.JiraRestClientFactory;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

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

		// Construct the JRJC client
		System.out.println(String.format("Logging in to %s with username '%s'", input.jiraURL, input.jiraUsername));
		JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
		URI uri;

		try {

			uri = new URI(input.jiraURL);
			JiraRestClient client = factory.createWithBasicHttpAuthentication(uri, input.jiraUsername, input.jiraPassword);

			// fetch events with jira issue URLs
			JiraEventList jiraEvents = JiraIntegrationUtil.fetch(args, input);

			// populate Jira data
			jiraEvents.populate(client);
			jiraEvents.sync();

			// System.out.println(jiraEvents);

		} catch (URISyntaxException e) {
			System.out.println("Caught URISyntaxException. Check jiraURL and try again.");
			System.out.println(e.getMessage());
			System.exit(1);
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

		// validate days
		if (input.days <= 0)
			throw new IllegalArgumentException("'days' must be at least 1 day");

		if (input.jiraURL == null || input.jiraURL.isEmpty())
			throw new IllegalArgumentException("'jiraURL' is required");

		if (input.jiraUsername == null || input.jiraUsername.isEmpty())
			throw new IllegalArgumentException("'jiraUsername' is required");

		if (input.jiraPassword == null || input.jiraPassword.isEmpty())
			throw new IllegalArgumentException("'jiraPassword' is required");

		if (input.resolvedStatus == null || input.resolvedStatus.isEmpty())
			throw new IllegalArgumentException("'resolvedStatus' is required");

		if (input.hiddenStatus == null || input.hiddenStatus.isEmpty())
			throw new IllegalArgumentException("'hiddenStatus' is required");

		if (input.resurfacedStatus == null || input.resurfacedStatus.isEmpty())
			throw new IllegalArgumentException("'resurfacedStatus' is required");

		return input;
	}

	static class JiraIntegrationInput extends Input {
		public int days; // in days

		public String jiraURL;
		public String jiraUsername;
		public String jiraPassword;

		public String resolvedStatus;
		public String hiddenStatus;
		public String resurfacedStatus;

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

	// for testing in aws lambda
	public static void lambdaHandler(String input, Context context) {
		System.out.println("lambdaHandler()");
		System.out.println(input);
		main(input.split(" "));
	}

	// for testing locally
	public static void main(String[] args) {
		Instant start = Instant.now(); // timer

		if ((args == null) || (args.length < 10))
			throw new IllegalArgumentException(
				"java JiraIntegrationFunction API_URL API_KEY SERVICE_ID JIRA_URL JIRA_USER JIRA_PASS " + 
				"DAYS RESOLVED_STATUS HIDDEN_STATUS RESURFACED_STATUS");

		ContextArgs contextArgs = new ContextArgs();

		contextArgs.apiHost = args[0];
		contextArgs.apiKey = args[1];
		contextArgs.serviceId = args[2];

		// Use "Jira UDF" View
		SummarizedView view = ViewUtil.getServiceViewByName(contextArgs.apiClient(), contextArgs.serviceId, "Jira UDF");
		contextArgs.viewId = view.id;

		// some test values
		String[] sampleValues = new String[] {
			"jiraURL=" + args[3],
			"jiraUsername=" + args[4],
			"jiraPassword=" + args[5],
			"days=" + args[6], // 14
			"resolvedStatus=" + args[7], // Resolved
			"hiddenStatus=" + args[8], // Won't Fix, Closed
			"resurfacedStatus=" + args[9] // Reopened
		};

		String rawContextArgs = new Gson().toJson(contextArgs);
		JiraIntegrationFunction.execute(rawContextArgs, String.join("\n", sampleValues));

		Instant finish = Instant.now(); // timer
		long timeElapsed = Duration.between(start, finish).toMillis();  //in millis

		System.out.print("Sync complete. Time elapsed: ");
		System.out.print(timeElapsed);
		System.out.println("ms");
	}
}

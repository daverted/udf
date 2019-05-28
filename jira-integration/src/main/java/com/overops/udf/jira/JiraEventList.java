package com.overops.udf.jira;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.domain.Issue;
import com.atlassian.jira.rest.client.domain.SearchResult;
import com.overops.udf.jira.JiraEvent.Status;
import com.overops.udf.jira.JiraIntegrationFunction.JiraIntegrationInput;
import com.takipi.api.client.request.label.BatchModifyLabelsRequest;
import com.takipi.api.client.request.label.BatchModifyLabelsRequest.Builder;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.udf.ContextArgs;

public class JiraEventList {
	private HashMap<String, JiraEvent> eventList;
	private JiraIntegrationInput input;
	private ContextArgs args;

	public JiraEventList(JiraIntegrationInput input, ContextArgs args) {
		this.eventList = new HashMap<String, JiraEvent>(1400);
		this.input = input;
		this.args = args;
	}

	public void addEvent(String issueId, EventResult event) {
		if (eventList.containsKey(issueId)) {
			eventList.get(issueId).events.add(event);
		} else {
			JiraEvent jiraEvent = new JiraEvent(event);
			eventList.put(issueId, jiraEvent);
		}
	}

	public HashMap<String, JiraEvent> getEventList() {
		return eventList;
	}

	public void sync(JiraRestClient client) {
		populate(client);
		syncBatch();
	}

	// populate Jira data
	private void populate(JiraRestClient client) {

		if (eventList.size() < 1) {
			System.out.println("Event list is empty.");
			return;
		}

		System.out.println("(jira event list) jiraEvents:");
		System.out.println(this);
		System.out.println();

		StringBuilder updateKeys = new StringBuilder("issuekey in (");
		for (String key : eventList.keySet()) {
			updateKeys.append(key);
			updateKeys.append(", ");
		}

		// remove final ", " and close )
		String updateKeysStr = updateKeys.toString();
		updateKeysStr = updateKeys.substring(0, updateKeys.length() - 2) + ")";

		SearchResult updateKeysResult = client.getSearchClient().searchJql(updateKeysStr, 1000, 0).claim();

		Set<String> keys = new HashSet<String>();
		keys.addAll(eventList.keySet());

		HashMap<String, JiraEvent> tempList = new HashMap<String, JiraEvent>();

		updateKeysResult.getIssues().forEach((issue) -> {
			String key = issue.getKey();
			keys.remove(key);
		});

		if (!keys.isEmpty()) {
			keys.forEach((key) -> {
				Issue issue = client.getIssueClient().getIssue(key).claim();
				String issueKey = issue.getKey();

				JiraEvent event = eventList.get(key);
				tempList.put(issueKey, event);
			});
			eventList.keySet().removeAll(keys);
			eventList.putAll(tempList);
		}

		System.out.println(">>> updated eventList: ");
		System.out.println(eventList);

		StringBuilder jqlHidden = new StringBuilder("status = \"");
		jqlHidden.append(input.hiddenStatus);
		jqlHidden.append("\" AND issuekey in (");

		for (String key : eventList.keySet()) {
			jqlHidden.append(key);
			jqlHidden.append(", ");
		}

		// remove final ", " and close )
		String jqlHiddenStr = jqlHidden.toString();
		jqlHiddenStr = jqlHiddenStr.substring(0, jqlHiddenStr.length() - 2) + ")";

		// remove hidden from list, then search for resolved
		Set<String> unknownKeys = eventList.keySet();

		SearchResult hidden = client.getSearchClient().searchJql(jqlHiddenStr, 1000, 0).claim();
		hidden.getIssues().forEach((basicIssue) -> {
			String key = basicIssue.getKey();
			eventList.get(key).issueStatus = Status.HIDDEN;
			unknownKeys.remove(key);
		});

		if (unknownKeys.size() < 1) {
			return;
		}

		StringBuilder jqlResolved = new StringBuilder("status = \"");
		jqlResolved.append(input.resolvedStatus);
		jqlResolved.append("\" AND issuekey in (");

		for (String key : unknownKeys) {
			jqlResolved.append(key);
			jqlResolved.append(", ");
		}

		// remove final ", " and close )
		String jqlResolvedStr = jqlResolved.toString();
		jqlResolvedStr = jqlResolvedStr.substring(0, jqlResolvedStr.length() - 2) + ")";

		SearchResult resolved = client.getSearchClient().searchJql(jqlResolvedStr, 1000, 0).claim();
		resolved.getIssues().forEach((basicIssue) -> {
			String key = basicIssue.getKey();
			eventList.get(key).issueStatus = Status.RESOLVED;
		});

	}

	private void syncBatch() {
		Builder batchBuilder = BatchModifyLabelsRequest.newBuilder().setServiceId(args.serviceId);

		// for each JiraEvent:
		System.out.println("syncing " + eventList.size() + " issues");
		eventList.forEach((issueId, jiraEvent) -> {
			jiraEvent.events.forEach(eventResult -> {
				Status eventStatus = JiraEvent.status(eventResult);
				if (jiraEvent.issueStatus != eventStatus) {
					System.out.println(">> update event! (" + eventResult.id + ") issueStatus: " + jiraEvent.issueStatus + " eventStatus: " + eventStatus);

					List<String> addLabels = new LinkedList<String>();
					addLabels.add(jiraEvent.issueStatus.getLabel());

					List<String> removeLabels = new ArrayList<String>();
					removeLabels.add(eventStatus.getLabel());

					batchBuilder.addLabelModifications(eventResult.id, addLabels, removeLabels);
				}
			});
		});

		try {
			// post batch label change request
			// 
			// args.apiClient().post(batchBuilder.setHandleSimilarEvents(false).build());
			//
			System.out.println("dry run");
		} catch (IllegalArgumentException ex) {
			// this is normal - it happens when there are no modifications to be made
			System.out.println(ex.getMessage());
		}
	}

	@Override
	public String toString() {
		return "{" +
			" eventList='" + getEventList() + "'" +
			"}\n";
	}

}
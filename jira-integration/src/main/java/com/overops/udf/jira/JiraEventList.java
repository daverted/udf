package com.overops.udf.jira;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.atlassian.jira.rest.client.JiraRestClient;
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
		this.eventList = new HashMap<String, JiraEvent>();
		this.input = input;
		this.args = args;
	}

	public void addEvent(String issueId, EventResult event) {
		if (this.eventList.containsKey(issueId)) {
			this.eventList.get(issueId).addEvent(event);
		} else {
			JiraEvent jiraEvent = new JiraEvent(input);
			jiraEvent.addEvent(event);
			this.eventList.put(issueId, jiraEvent);
		}
	}

	public HashMap<String, JiraEvent> getEventList() {
		return this.eventList;
	}

	public void populate(JiraRestClient client) {
		// populate Jira data

		StringBuilder jqlSb = new StringBuilder(" AND issuekey in (");

		if (eventList.size() < 1) {
			System.out.println("Event list is empty.");
			return;
		}

		for (String key : eventList.keySet()) {
			jqlSb.append(key);
			jqlSb.append(", ");
		}

		// remove final ", " and close )
		String jql = jqlSb.toString();
		jql = jql.substring(0, jql.length() - 2) + ")";

		String resolvedJql = "status = " + input.resolvedStatus + jql;
		String hiddenJql = "status = " + input.hiddenStatus + jql;

		System.out.println("resolvedJql: " + resolvedJql);
		System.out.println("hiddenJql: " + hiddenJql);

		SearchResult resolved = client.getSearchClient().searchJql(resolvedJql, 1000, 0).claim();
		resolved.getIssues().forEach((basicIssue) -> {
			eventList.get(basicIssue.getKey()).setIssueStatus(input.resolvedStatus);
		});

		SearchResult hidden = client.getSearchClient().searchJql(hiddenJql, 1000, 0).claim();
		hidden.getIssues().forEach((basicIssue) -> {
			eventList.get(basicIssue.getKey()).setIssueStatus(input.hiddenStatus);
		});
	}

	public void sync() {
		Builder batchBuilder = BatchModifyLabelsRequest.newBuilder().setServiceId(args.serviceId);

		// for each JiraEvent:
		System.out.println("syncing " + eventList.size() + " issues");
		eventList.forEach((issueId, jiraEvent) -> {
			Status issueStatus = jiraEvent.getIssueStatus();
			jiraEvent.getEvents().forEach(eventResult -> {
				Status eventStatus = JiraEvent.status(eventResult);
				if (issueStatus != eventStatus) {
					System.out.println(">> update event! (" + eventResult.id + ") issueStatus: " + issueStatus + " eventStatus: " + eventStatus);

					List<String> addLabels = new LinkedList<String>();
					addLabels.add(issueStatus.getLabel());

					List<String> removeLabels = new ArrayList<String>();
					removeLabels.add(eventStatus.getLabel());

					batchBuilder.addLabelModifications(eventResult.id, addLabels, removeLabels);
				}
			});
		});

		try {
			// post batch label change request
			args.apiClient().post(batchBuilder.setHandleSimilarEvents(false).build());
		} catch (IllegalArgumentException ex) {
			// this is normal - it happens when there are no modifications to be made
			System.out.println(ex.getMessage());
		}
	}

	@Override
	public String toString() {
		return "{" +
			" eventList='" + getEventList() + "'" +
			"}";
	}

}
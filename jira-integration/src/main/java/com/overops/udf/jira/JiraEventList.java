package com.overops.udf.jira;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.domain.Issue;
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
			this.eventList.get(issueId).addEvent(issueId, event);
		} else {
			JiraEvent jiraEvent = new JiraEvent(input, args);
			jiraEvent.addEvent(issueId, event);
			this.eventList.put(issueId, jiraEvent);
		}
	}

	public HashMap<String, JiraEvent> getEventList() {
		return this.eventList;
	}

	public void populate(JiraRestClient client) {
		// populate Jira data
		for (String key : eventList.keySet()) {
			Issue issue = client.getIssueClient().getIssue(key).claim();
			eventList.get(key).setIssue(issue);
		}
	}

	public void sync() {
		Builder batchBuilder = BatchModifyLabelsRequest.newBuilder().setServiceId(args.serviceId);
		ArrayList<JiraEvent> resurfacedEvents = new ArrayList<JiraEvent>();

		// for each JiraEvent:
		System.out.println("syncing " + eventList.size() + " issues");
		eventList.forEach((issueId, jiraEvent) -> {
			Status issueStatus = jiraEvent.getIssueStatus();
			jiraEvent.getEvents().forEach(eventResult -> {
				Status eventStatus = JiraEvent.status(eventResult);
				if (issueStatus != eventStatus) {
					if (eventStatus == Status.RESURFACED) {
						System.out.println("resurfaced event: " + jiraEvent);
					}

					System.out.println(">> update event! (" + eventResult.id + ") issueStatus: " + issueStatus + " eventStatus: " + eventStatus);

					List<String> addLabels = new LinkedList<String>();
					addLabels.add(issueStatus.getLabel());

					List<String> removeLabels = new ArrayList<String>();
					removeLabels.add(eventStatus.getLabel());

					batchBuilder.addLabelModifications(eventResult.id, addLabels, removeLabels);
				}
			});
		});

		// TODO resurfaced = deployment version needs to change <---

		// 	// get possible status transitions
		// 	Iterable<Transition> transitions = client.getIssueClient().getTransitions(issue).claim();

		// 	} else if (isResurfaced) {
		// 		// 3. resurfaced in OO → resurfaced in Jira
		// 		System.out.print(" Resurfaced in OverOps");

		// 		if (!isResurfacedStatus) {
		// 			System.out.print(" NOT Resurfaced in Jira. Resurfacing...");
		// 			for (Transition transition : transitions) {
		// 				if (transition.getName().equals(input.resurfacedStatus)) {
		// 					TransitionInput transitionInput = new TransitionInput(transition.getId());
		// 					client.getIssueClient().transition(issue, transitionInput).claim();
		// 					break;
		// 				}
		// 			}
		// 		}


		try {
			System.out.println("sync (dry run)");
			// temporarily commented out for testing
			// args.apiClient().post(batchBuilder.setHandleSimilarEvents(true).build());
			//
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
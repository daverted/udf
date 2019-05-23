package com.overops.udf.jira;

import java.util.ArrayList;
import java.util.List;

import com.overops.udf.jira.JiraIntegrationFunction.JiraIntegrationInput;
import com.takipi.api.client.result.event.EventResult;

public class JiraEvent {
	private JiraIntegrationInput input;

	private Status issueStatus; // jira status

	private List<EventResult> events; // overops events

	// possible statuses
	enum Status {
		RESOLVED("Resolved"),
		HIDDEN("Archive"),
		INBOX("Inbox");

		private String label;

		public String getLabel() {
			return label;
		}

		private Status(String label) {
			this.label = label;
		}
	}

	public JiraEvent(JiraIntegrationInput input) {
		this.events = new ArrayList<EventResult>();
		this.input = input;
		this.issueStatus = Status.INBOX; // defaults to inbox
	}

	public void addEvent(EventResult event) {
		events.add(event);
	}

	public List<EventResult> getEvents() {
		return events;
	}

	public void setIssueStatus(String issueStatusName) {
		if (issueStatusName.equals(input.resolvedStatus)) {
			issueStatus = Status.RESOLVED;
		} else if (issueStatusName.equals(input.hiddenStatus)) {
			issueStatus = Status.HIDDEN;
		} else {
			issueStatus = Status.INBOX;
		}
	}

	public Status getIssueStatus() {
		return issueStatus;
	}

	public static Status status(EventResult event) {
		if (event.labels.contains("Resolved")) return Status.RESOLVED;
		if (event.labels.contains("Archive")) return Status.HIDDEN;
		// if (event.labels.contains("Inbox")) return Status.INBOX; // default is inbox
		return Status.INBOX;
	}

	@Override
	public String toString() {
		return "{" +
			" issueStatus=" + getIssueStatus() + ", " +
			" events=" + getEvents() +
			"}";
	}

}
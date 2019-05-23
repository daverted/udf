package com.overops.udf.jira;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.atlassian.jira.rest.client.domain.Issue;
import com.overops.udf.jira.JiraIntegrationFunction.JiraIntegrationInput;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.udf.ContextArgs;

public class JiraEvent {
	private JiraIntegrationInput input;
	private ContextArgs args;

	private String issueId;
	private Issue issue;

	private List<EventResult> events;

	private boolean resurfaced; // is resurfaced in overops
	private Status issueStatus; // jira status

	private Instant eventDate; // most recent event date (can be null) for resurfaced comparison
	private Instant issueDate; 

	// possible statuses
	enum Status {
		RESOLVED("Resolved"),
		HIDDEN("Archive"),
		RESURFACED("Resurfaced"),
		INBOX("Inbox");

		private String label;

		public String getLabel() {
			return label;
		}

		private Status(String label) {
			this.label = label;
		}
	}

	public JiraEvent(JiraIntegrationInput input, ContextArgs args) {
		this.events = new ArrayList<EventResult>();
		this.input = input;
		this.args = args;
		this.resurfaced = false; // default
	}

	public void addEvent(String issueId, EventResult event) {
		if (this.issueId == null) {
			setIssueId(issueId);
		}

		if (this.issueId.equals(issueId)) {
			events.add(event);
		}

		// resurfaced
		if (status(event) == Status.RESURFACED) {
			this.resurfaced = true;

			// set last updated date and status (from OO)
			Instant date = JiraIntegrationUtil.getEventDate(event.id, args);
			if (date != null && (this.eventDate == null || date.isAfter(this.eventDate)) )
				setEventDate(date);
		}

	}

	public List<EventResult> getEvents() {
		return events;
	}

	public String getIssueId() {
		return issueId;
	}

	public void setIssueId(String issueId) {
		this.issueId = issueId;
	}

	public Issue getIssue() {
		return issue;
	}

	public void setIssue(Issue issue) {
		this.issue = issue;

		// parse and set status
		String issueStatusName = issue.getStatus().getName();
		if (issueStatusName.equals(input.resolvedStatus)) {
			issueStatus = Status.RESOLVED;
		} else if (issueStatusName.equals(input.hiddenStatus)) {
			issueStatus = Status.HIDDEN;
		} else if (issueStatusName.equals(input.resurfacedStatus)) {
			issueStatus = Status.RESURFACED;
		} else {
			issueStatus = Status.INBOX;
		}

		// parse date and set date
		setIssueDate(issue.getUpdateDate().getMillis());
	}

	private void setIssueDate(long millis) {
		// round to the nearest minute for more sensible comparisons
		issueDate = Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.MINUTES);
	}

	private void setEventDate(Instant date) {
		// round to minutes for more sensible date comparisons
		eventDate = date.truncatedTo(ChronoUnit.MINUTES);
	}

	public Instant getEventDate() {
		return eventDate;
	}

	public Instant getIssueDate() {
		return issueDate;
	}

	public Status getIssueStatus() {
		return issueStatus;
	}

	public boolean isResurfaced() {
		return resurfaced;
	}

	public static Status status(EventResult event) {
		if (event.labels.contains("Resolved")) return Status.RESOLVED;
		if (event.labels.contains("Archive")) return Status.HIDDEN;
		if (event.labels.contains("Resurfaced")) return Status.RESURFACED; // order is important, resurfaced is also in inbox
		// if (event.labels.contains("Inbox")) return Status.INBOX; // default is inbox
		return Status.INBOX;
	}

	@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			if (!(o instanceof JiraEvent)) {
				return false;
			}
			JiraEvent jiraEvent = (JiraEvent) o;
			return Objects.equals(issueId, jiraEvent.issueId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(issueId);
	}

	@Override
	public String toString() {
		return "{" +
			" issueId='" + getIssueId() + ", " +
			" issueStatus=" + getIssueStatus() + ", " +
			" resurfaced=" + isResurfaced() + ", " +
			" issueDate=" + getIssueDate() + ", " +
			" eventDate=" + getEventDate() + ", " +
			" events=" + getEvents() + "}";
	}

}
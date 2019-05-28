package com.overops.udf.jira;

import java.util.ArrayList;
import java.util.List;

import com.takipi.api.client.result.event.EventResult;

public class JiraEvent {
	public Status issueStatus; // jira status
	public List<EventResult> events; // overops events

	// possible statuses
	public enum Status {
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

	public JiraEvent(EventResult event) {
		this.events = new ArrayList<EventResult>(5);
		this.issueStatus = Status.INBOX; // defaults to inbox
		events.add(event);
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
			" issueStatus=" + issueStatus + ", " +
			" events=" + events +
			"}\n";
	}

}
package com.example.udf.snapshot;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.common.base.Strings;
import com.google.gson.Gson;

import com.takipi.api.client.ApiClient;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.request.event.BatchForceSnapshotsRequest;
import com.takipi.api.client.request.event.EventsSlimVolumeRequest;
import com.takipi.api.client.result.EmptyResult;
import com.takipi.api.client.result.event.EventSlimResult;
import com.takipi.api.client.result.event.EventsSlimVolumeResult;
import com.takipi.api.client.util.validation.ValidationUtil.VolumeType;
import com.takipi.api.client.util.view.ViewUtil;
import com.takipi.api.core.url.UrlClient.Response;
import com.takipi.common.util.CollectionUtil;
import com.takipi.udf.ContextArgs;
import com.takipi.udf.input.Input;

public class ForceSnapshotFunction {
	public static String validateInput(String rawInput) {
		return getForceSnapshotInput(rawInput).toString();
	}

	public static void execute(String rawContextArgs, String rawInput) {
		ForceSnapshotInput input = getForceSnapshotInput(rawInput);

		ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

		System.out.println("execute context: " + rawContextArgs);

		if (!args.validate())
			throw new IllegalArgumentException("Bad context args: " + rawContextArgs);

		if (!args.viewValidate())
			return;

		ApiClient apiClient = args.apiClient();

		// get all events that have occurred in the last {timespan} minutes
		DateTime to = DateTime.now();
		DateTime from = to.minusMinutes(input.timespan);

		DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

		// EventsSlimVolumeRequest is similar to EventsRequest but without extra
		// metadata
		EventsSlimVolumeRequest eventsRequest = EventsSlimVolumeRequest.newBuilder().setServiceId(args.serviceId)
				.setViewId(args.viewId).setFrom(from.toString(fmt)).setTo(to.toString(fmt))
				.setVolumeType(VolumeType.all).build();

		Response<EventsSlimVolumeResult> eventsResponse = apiClient.get(eventsRequest);

		if (eventsResponse.isBadResponse())
			throw new IllegalStateException("Failed getting events.");

		EventsSlimVolumeResult eventsResult = eventsResponse.data;

		if (CollectionUtil.safeIsEmpty(eventsResult.events)) {
			System.out.println("Found no events from the last " + input.timespan + " minutes.");
			return;
		}

		List<EventSlimResult> events = eventsResult.events;
		List<String> eventIds = new ArrayList<String>();

		for (EventSlimResult result : events) {
			System.out.println("event id: " + result.id);
			eventIds.add(result.id);
		}

		// force snapshot all of the retrieved events
		BatchForceSnapshotsRequest batchRequest = BatchForceSnapshotsRequest.newBuilder().addEventIds(eventIds)
				.setServiceId(args.serviceId).build();

		Response<EmptyResult> batchResponse = apiClient.post(batchRequest);

		if (batchResponse.isBadResponse())
			throw new IllegalStateException("Batch Force Snapshot failed");

		System.out.println("Batch Force Snapshots Request Sent!");
	}

	private static ForceSnapshotInput getForceSnapshotInput(String rawInput) {
		System.out.println("rawInput:" + rawInput);

		if (Strings.isNullOrEmpty(rawInput))
			throw new IllegalArgumentException("Input is empty");

		ForceSnapshotInput input;

		try {
			input = ForceSnapshotInput.of(rawInput);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (input.timespan <= 0)
			throw new IllegalArgumentException("'timespan' must be positive");

		return input;
	}

	static class ForceSnapshotInput extends Input {
		public int timespan;

		private ForceSnapshotInput(String raw) {
			super(raw);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();

			builder.append("Force Snapshot (");
			builder.append(timespan);
			builder.append(" min)");

			return builder.toString();
		}

		static ForceSnapshotInput of(String raw) {
			return new ForceSnapshotInput(raw);
		}
	}

	// A sample program on how to programmatically activate
	public static void main(String[] args) {
		if ((args == null) || (args.length < 3))
			throw new IllegalArgumentException("java ForceSnapshotFunction API_URL API_KEY SERVICE_ID");

		ContextArgs contextArgs = new ContextArgs();

		contextArgs.apiHost = args[0];
		contextArgs.apiKey = args[1];
		contextArgs.serviceId = args[2];

		SummarizedView view = ViewUtil.getServiceViewByName(contextArgs.apiClient(), contextArgs.serviceId,
				"EventGenerator");
		// "Custom OverOps Event");
		// "All Events");

		contextArgs.viewId = view.id;

		System.out.println("current time: " + DateTime.now() );
		System.out.println("view id: " + view.id);

		String rawContextArgs = new Gson().toJson(contextArgs);
		ForceSnapshotFunction.execute(rawContextArgs, "timespan=5");
	}
}

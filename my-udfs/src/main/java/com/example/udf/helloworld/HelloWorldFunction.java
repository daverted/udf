package com.example.udf.helloworld;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.takipi.api.client.ApiClient;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.request.event.EventModifyLabelsRequest;
import com.takipi.api.client.request.label.CreateLabelRequest;
import com.takipi.api.client.request.event.EventsRequest;
import com.takipi.api.client.result.EmptyResult;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.result.event.EventsResult;
import com.takipi.api.core.url.UrlClient.Response;
import com.takipi.api.client.util.view.ViewUtil;
import com.takipi.common.util.CollectionUtil;
import com.takipi.udf.ContextArgs;
import com.takipi.udf.input.Input;

public class HelloWorldFunction {
	public static String validateInput(String rawInput) {
		return getLabelInput(rawInput).toString();
	}

	private static LabelInput getLabelInput(String rawInput) {
		System.out.println("validateInput rawInput:" + rawInput);

		if (Strings.isNullOrEmpty(rawInput))
			throw new IllegalArgumentException("Input is empty");

		LabelInput input;

		try {
			input = LabelInput.of(rawInput);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (StringUtils.isEmpty(input.label))
			throw new IllegalArgumentException("Label name can't be empty");

		return input;
	}

	public static void execute(String rawContextArgs, String rawInput) {
		LabelInput input = getLabelInput(rawInput);

		ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

		System.out.println("execute context: " + rawContextArgs);

		if (!args.validate())
			throw new IllegalArgumentException("Bad context args: " + rawContextArgs);

		if (!args.eventValidate())
			return;

		ApiClient apiClient = args.apiClient();

		CreateLabelRequest createLabel = CreateLabelRequest.newBuilder().setServiceId(args.serviceId)
				.setName(input.label).build();

		Response<EmptyResult> createResult = apiClient.post(createLabel);

		if (createResult.isBadResponse())
			throw new IllegalStateException("Can't create label " + input);

		EventModifyLabelsRequest addLabel = EventModifyLabelsRequest.newBuilder().setServiceId(args.serviceId)
				.setEventId(args.eventId).addLabel(input.label).build();

		Response<EmptyResult> addResult = apiClient.post(addLabel);

		if (addResult.isBadResponse())
			throw new IllegalStateException("Can't apply label " + input + " to event " + args.eventId);
	}

	static class LabelInput extends Input {
		public String label;

		private LabelInput(String raw) {
			super(raw);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();

			builder.append("Label name - ");
			builder.append(label);

			return builder.toString();
		}

		static LabelInput of(String raw) {
			return new LabelInput(raw);
		}
	}

	// A sample program on how to programmatically activate
	public static void main(String[] args) {

		// pass API Host, Key, and Service ID as command line arguments
		if ((args == null) || (args.length < 3))
			throw new IllegalArgumentException("java HelloWorldFunction API_URL API_KEY SERVICE_ID");

		// create new ContextArgs from command line arguments
		ContextArgs contextArgs = new ContextArgs();

		contextArgs.apiHost = args[0];
		contextArgs.apiKey = args[1];
		contextArgs.serviceId = args[2];

		// get "All Events" view
		SummarizedView view = ViewUtil.getServiceViewByName(contextArgs.apiClient(), contextArgs.serviceId,
				"All Events");
		contextArgs.viewId = view.id;

		// get an event that has occurred in the last 5 minutes
		DateTime to = DateTime.now();
		DateTime from = to.minusMinutes(5);

		// date parameter must be properly formatted
		DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

		// get all events within the date range
		EventsRequest eventsRequest = EventsRequest.newBuilder().setServiceId(contextArgs.serviceId)
				.setViewId(contextArgs.viewId).setFrom(from.toString(fmt)).setTo(to.toString(fmt)).build();

		// create a new API Client
		ApiClient apiClient = contextArgs.apiClient();

		// execute API GET request
		Response<EventsResult> eventsResponse = apiClient.get(eventsRequest);

		// check for a bad API response
		if (eventsResponse.isBadResponse())
			throw new IllegalStateException("Failed getting events.");

		// retrieve event data from the result
		EventsResult eventsResult = eventsResponse.data;

		// exit if there are no events - increase date range if this occurs
		if (CollectionUtil.safeIsEmpty(eventsResult.events)) {
			System.out.println("NO EVENTS");
			return;
		}

		// retrieve a list of events from the result
		List<EventResult> events = eventsResult.events;

		// get the first event
		contextArgs.eventId = events.get(0).id;

		// set label to 'Hello_World_{eventId}'
		String rawInput = "label=Hello_World_" + contextArgs.eventId;

		// convert context args to a JSON string
		String rawContextArgs = new Gson().toJson(contextArgs);

		// execute the UDF
		HelloWorldFunction.execute(rawContextArgs, rawInput);
	}
}

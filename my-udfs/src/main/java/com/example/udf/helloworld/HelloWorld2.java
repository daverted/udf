package com.example.udf.helloworld;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.takipi.api.client.ApiClient;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.request.event.EventsVolumeRequest;
import com.takipi.api.client.result.event.EventsVolumeResult;
import com.takipi.api.client.util.validation.ValidationUtil.VolumeType;
import com.takipi.api.client.util.view.ViewUtil;
import com.takipi.api.core.url.UrlClient.Response;

public class HelloWorld2 {

	public static void main(String[] args) {

		String serviceId = "S37295";

		ApiClient apiClient = ApiClient.newBuilder().setHostname("https://api.overops.com")
				.setApiKey("***REMOVED***").build();

		// get "All Events" view
		SummarizedView view = ViewUtil.getServiceViewByName(apiClient, serviceId, "All Events");

		// get events that have occurred in the last 5 minutes
		DateTime to = DateTime.now();
		DateTime from = to.minusMinutes(5);

		// date parameter must be properly formatted
		DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

		// get all events within the date range
		EventsVolumeRequest eventsVolumeRequest = EventsVolumeRequest.newBuilder().setServiceId(serviceId)
				.setFrom(from.toString(fmt)).setTo(to.toString(fmt)).setViewId(view.id).setVolumeType(VolumeType.all)
				.build();

		// GET event data
		Response<EventsVolumeResult> eventsVolumeResponse = apiClient.get(eventsVolumeRequest);

		// check for a bad API response (HTTP status code >= 300)
		if (eventsVolumeResponse.isBadResponse())
			throw new IllegalStateException("Failed getting events.");

		EventsVolumeResult eventsVolumeResult = eventsVolumeResponse.data;

		System.out.println("Found " + eventsVolumeResult.events.size() + " events"); 
		System.out.println(eventsVolumeResult.events);

	}
}

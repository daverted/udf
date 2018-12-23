package com.example.udf.snapshot;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.gson.Gson;
import com.takipi.api.client.ApiClient;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.request.event.BatchForceSnapshotsRequest;
import com.takipi.api.client.request.event.EventsRequest;
import com.takipi.api.client.result.EmptyResult;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.result.event.EventsResult;
import com.takipi.api.client.util.view.ViewUtil;
import com.takipi.api.core.url.UrlClient.Response;
import com.takipi.common.util.CollectionUtil;
import com.takipi.udf.ContextArgs;
import com.takipi.udf.input.Input;

public class ForceSnapshotFunction {
  public static String validateInput(String rawInput) {
    return "Force Snapshot"; // there are no input parameters for this function
  }

  public static void execute(String rawContextArgs, String rawInput) {
    ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

    System.out.println("execute context: " + rawContextArgs);

    if (!args.validate()) throw new IllegalArgumentException("Bad context args: " + rawContextArgs);

    if (!args.viewValidate()) return;

    ApiClient apiClient = args.apiClient();

    // get all events that have occurred in the last 5 minutes
    DateTime to = DateTime.now();
    DateTime from = to.minusMinutes(5);

    DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

    EventsRequest eventsRequest = EventsRequest.newBuilder()
      .setServiceId(args.serviceId)
      .setViewId(args.viewId)
      .setFrom(from.toString(fmt))
      .setTo(to.toString(fmt))
      .build();

    Response<EventsResult> eventsResponse = apiClient.get(eventsRequest);

    if (eventsResponse.isBadResponse())
      throw new IllegalStateException("Failed getting events.");

    EventsResult eventsResult = eventsResponse.data;

    if (CollectionUtil.safeIsEmpty(eventsResult.events)) {
      System.out.println("Found no events from the last 5 minutes.");
      return;
    }

    List<EventResult> events = eventsResult.events;
    List<String> eventIds = new ArrayList<String>();

    for (EventResult result : events) {
      System.out.println("event id: " + result.id);
      eventIds.add(result.id);
    }

    // force snapshot all of the retrieved events
    BatchForceSnapshotsRequest batchRequest = BatchForceSnapshotsRequest.newBuilder()
        .addEventIds(eventIds)
        .setServiceId(args.serviceId)
        .build();

    Response<EmptyResult> batchResponse = apiClient.post(batchRequest);

    if (batchResponse.isBadResponse())
      throw new IllegalStateException("Batch Force Snapshot failed");

  }

  static class ForceSnapshotInput extends Input {
    // no input parameters

    private ForceSnapshotInput(String raw) {
      super(raw);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("Force Snapshot");
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

    SummarizedView view = ViewUtil.getServiceViewByName(
      contextArgs.apiClient(),
      contextArgs.serviceId,
      "All Events");

      contextArgs.viewId = view.id;

    String rawContextArgs = new Gson().toJson(contextArgs);
    ForceSnapshotFunction.execute(rawContextArgs, "");
  }
}

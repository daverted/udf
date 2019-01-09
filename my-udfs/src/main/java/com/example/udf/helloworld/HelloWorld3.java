package com.example.udf.helloworld;

import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;

import com.takipi.api.client.ApiClient;
import com.takipi.api.client.data.transaction.Stats;
import com.takipi.api.client.data.transaction.TransactionGraph;
import com.takipi.api.client.data.transaction.TransactionGraph.GraphPoint;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.util.transaction.TransactionUtil;
import com.takipi.api.client.util.view.ViewUtil;

public class HelloWorld3 {

  public static void main(String[] args) {

    String serviceId = "S37295";

    ApiClient apiClient = ApiClient.newBuilder().setHostname("https://api.overops.com")
        .setApiKey("fP5cAd73HOSpn8b5VJTznj5MUrxPXFW3jwzJq2++").build();

    // get "All Events" view
    SummarizedView view = ViewUtil.getServiceViewByName(apiClient, serviceId, "All Events");

    // get events that have occurred in the last 5 minutes
    DateTime to = DateTime.now();
    DateTime from = to.minusHours(5);

    Map<String, TransactionGraph> transactionGraphs = TransactionUtil.getTransactionGraphs(apiClient, serviceId, view.id, from, to, 10);

    Set<String> keySet = transactionGraphs.keySet();

    for(String k : keySet) {
      TransactionGraph graph = transactionGraphs.get(k);
      System.out.println("class name: " + graph.class_name);
      System.out.println("method name: " + graph.method_name);
      System.out.println();

      GraphPoint point = graph.points.get(0);
      System.out.println("point 0 timestamp: " + point.time);

      Stats stats = point.stats;
      System.out.println("point 0 total time: " + stats.total_time);
      System.out.println("point 0 average time: " + stats.avg_time);
      System.out.println("point 0 average time standard deviation: " + stats.avg_time_std_deviation);
      System.out.println("point 0 invocations: " + stats.invocations);
      System.out.println();
    }

  }
}

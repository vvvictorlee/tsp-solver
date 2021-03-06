package com.celsius.tsp.solver.rx;

import static io.reactivex.Single.just;

import com.celsius.tsp.common.CommonProblemFunctions;
import com.celsius.tsp.common.CommonSolutionFunctions;
import com.celsius.tsp.proto.TspService;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import io.reactivex.Observable;
import io.reactivex.Single;
import lombok.extern.log4j.Log4j2;

import java.util.List;


/**
 * Reactive implementation of the Nearest Neighbour Heuristic.
 *
 * @since 1.0.0
 * @author marc.bramaud
 */
@Log4j2
public class RxNearestNeighbourHeuristic implements ReactiveTravellingSalesmanHeuristic {
  private final MetricRegistry registry = new MetricRegistry();
  private final Timer timer = registry.timer(MetricRegistry.name(this.getClass(), "solve"));
  private final JmxReporter reporter = JmxReporter.forRegistry(registry).build();

  public RxNearestNeighbourHeuristic() {
    reporter.start();
  }

  @Override
  public Single<TspService.TravellingSalesmanSolution>
      solve(TspService.TravellingSalesmanProblem problem) {

    log.debug("Starting Nearest Neighbour reactive heuristic.");
    final Timer.Context context = timer.time();

    List<TspService.Vertex> visited = Lists.newArrayList();
    visited.add(CommonProblemFunctions.getVertexById(problem, problem.getDepartureVertexId()));

    TspService.Vertex current = CommonProblemFunctions
        .getVertexById(problem, problem.getDepartureVertexId());

    return getRecursive(visited, problem, current)
      .doOnError(throwable -> log.error("Error while executing heuristic: ", throwable))
      .map(vertices -> {
        // virtually add arrival vertex
        vertices.add(CommonProblemFunctions.getVertexById(problem, problem.getArrivalVertexId()));
        context.stop();
        log.debug("Done with Nearest Neighbour reactive heuristic.");
        return TspService.TravellingSalesmanSolution
          .newBuilder()
          .addAllVertices(vertices)
          .setCost(
            CommonSolutionFunctions.calculateWeightFromEdgesAndSolution(
              problem.getEdgesList(),
              vertices)
          )
          .build();
      });
  }

  private Single<List<TspService.Vertex>> getRecursive(List<TspService.Vertex> visited,
                                                       TspService.TravellingSalesmanProblem problem,
                                                       TspService.Vertex current) {
    if (visited.size() >= problem.getVerticesCount() - 1) {
      return just(visited);
    }
    return getNearestNotVisitedNeighbour(problem, current, visited)
      .flatMap(vertex -> {
        visited.add(vertex);
        return getRecursive(visited, problem, vertex);
      });
  }

  private Single<TspService.Vertex>
      getNearestNotVisitedNeighbour(TspService.TravellingSalesmanProblem problem,
                                    TspService.Vertex current,
                                    List<TspService.Vertex> visited) {
    return getEdgesToNeighbours(problem, current)
      .sorted((edge, edge2) -> Long.compare(edge.getValue(), edge2.getValue()))
      .map(edge -> CommonProblemFunctions.getVertexById(problem, edge.getArrivalVerticeId()))
      .filter(vertex -> !CommonProblemFunctions.isVertexVisited(visited, vertex)
        && !CommonProblemFunctions.isVertexArrival(problem, vertex))
      .firstOrError();
  }

  private Observable<TspService.Edge>
      getEdgesToNeighbours(TspService.TravellingSalesmanProblem problem,
                           TspService.Vertex current) {
    return Observable.fromIterable(problem.getEdgesList())
      .filter(edge -> edge.getDepartureVerticeId() == current.getId());
  }
}

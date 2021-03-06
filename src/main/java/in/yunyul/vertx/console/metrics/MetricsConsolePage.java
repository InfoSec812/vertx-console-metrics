package in.yunyul.vertx.console.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import in.yunyul.prometheus.extras.AdditionalJVMExports;
import in.yunyul.prometheus.extras.AdditionalVertxExports;
import in.yunyul.prometheus.extras.DropwizardTimerRateExports;
import in.yunyul.vertx.console.base.ConsolePage;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.vertx.MetricsHandler;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

@SuppressWarnings("unused")
public class MetricsConsolePage implements ConsolePage {
    private static final String JSON_CONTENT_TYPE = "application/json";

    private CollectorRegistry registry;

    /**
     * Creates the metrics console page, using the provided Dropwizard registry
     * and the default collector registry
     *
     * @param dropwizardRegistry the Dropwizard registry
     * @return the console page
     */
    public static MetricsConsolePage create(MetricRegistry dropwizardRegistry) {
        return create(dropwizardRegistry, CollectorRegistry.defaultRegistry);
    }

    /**
     * Creates the metrics console page, using the provided Dropwizard registry
     * and the provided collector registry
     *
     * @param dropwizardRegistry the Dropwizard registry
     * @param registry           the collector registry
     * @return the console page
     */
    public static MetricsConsolePage create(MetricRegistry dropwizardRegistry, CollectorRegistry registry) {
        registry.register(new DropwizardExports(dropwizardRegistry));
        DefaultExports.initialize();
        new AdditionalJVMExports().register();
        new DropwizardTimerRateExports(dropwizardRegistry).register();
        new AdditionalVertxExports().register();
        return create(registry);
    }

    /**
     * Creates the metrics console page, using the default collector registry
     *
     * @return the console page
     */
    public static MetricsConsolePage create() {
        return new MetricsConsolePage(CollectorRegistry.defaultRegistry);
    }

    /**
     * Creates the metrics console page, using the provided collector registry
     *
     * @param registry the collector registry
     * @return the console page
     */
    public static MetricsConsolePage create(CollectorRegistry registry) {
        return new MetricsConsolePage(registry);
    }

    private MetricsConsolePage(CollectorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void mount(Vertx vertx, Router router, String basePath) {
        router.route(basePath + "/metrics").handler(new MetricsHandler(registry));
        router.route(basePath + "/verticle*").handler(BodyHandler.create());
        router.route(basePath + "/verticle/deploy")
                .consumes(JSON_CONTENT_TYPE).produces(JSON_CONTENT_TYPE)
                .handler(ctx -> {
                    JsonObject body = ctx.getBodyAsJson();
                    String name = body.getString("name");
                    boolean isWorker = body.getBoolean("isWorker");
                    boolean ha = body.getBoolean("ha");
                    int instances = body.getInteger("instances");
                    JsonObject deployConfig = body.getJsonObject("deployConfig");

                    DeploymentOptions deploymentOptions = new DeploymentOptions()
                            .setWorker(isWorker)
                            .setHa(ha)
                            .setInstances(instances);
                    if (deployConfig != null && deployConfig.size() > 0) {
                        deploymentOptions.setConfig(deployConfig);
                    }

                    // TODO: Completion handler
                    vertx.deployVerticle(name, deploymentOptions,
                            result -> {
                                JsonObject response = new JsonObject();
                                if (result.succeeded()) {
                                    response.put("status", 200);
                                } else {
                                    response.put("status", 400);
                                }
                                ctx.response().putHeader("content-type", JSON_CONTENT_TYPE).end(response.toString());
                            });
                });
    }

    @Override
    public String getLoaderFileName() {
        return "/js/metrics.js";
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

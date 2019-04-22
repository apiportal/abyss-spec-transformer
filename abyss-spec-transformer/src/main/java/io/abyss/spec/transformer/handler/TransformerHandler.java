package io.abyss.spec.transformer.handler;

import io.abyss.spec.transformer.OpenAPITransformer;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import static io.abyss.spec.transformer.util.Constants.*;

/**
 * @author hakdogan (hakdogan@kodcu.com)
 * Created on 2019-03-22
 */

@Slf4j
public class TransformerHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext context) {

        try {

            final JsonObject config = context.getBodyAsJson();
            final String path = config.getString("path");
            final OpenAPITransformer transformer = new OpenAPITransformer();
            final JsonObject result = transformer.transform(path);

            context.response().putHeader(CONTENT_TYPE_ENTITY_HEADER, TEXT_CONTENT)
                    .setStatusCode(HTTP_OK_STATUS).end(result.getString("yamlFile"));

        } catch (Exception e) {
            log.error("An exception was thrown in handle method.", e);
            context.response().setStatusCode(HTTP_INTERNAL_ERROR).end(e.getMessage());
        }
    }
}

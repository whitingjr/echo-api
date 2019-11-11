package org.jboss.echoapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class EchoService extends AbstractVerticle {
   private static final CharSequence RESPONSE_TEXT = "pong";
   private static final CharSequence RESPONSE_LENGTH = Integer.toString(RESPONSE_TEXT.length()); 
   private static final String CSV = "\"5353153135.benchmark\",\"/1?user_key=535313531531531\"\r\n\"2539757573.benchmark\",\"/1?app_id=64646060&key_id=5731751351351\"\r\n\"7153175991.benchmark\",\"/11?app_id=64646060&key_id=5731751351351\"\r\n\"6052632834.benchmark\",\"/1111?app_id=64646060&key_id=5731751351351\"\r\n";
   private static final CharSequence CSV_RESPONSE_LENGTH = Integer.toString(CSV.length());
   private static final CharSequence TEXT_CSV = "text/csv";
   private static final Logger LOGGER = Logger.getLogger(EchoService.class.getName());
   private static final String LS = System.lineSeparator();
   public static final String HOSTS_URL = "/hosts";
   public static final int SERVICE_PORT = 8099;
   public static final String HYPERFOIL_CONFIG_URL = "/hyperfoil-ready-buddy-data";

   public void start() throws Exception {
      Router router = Router.router(vertx);
      router.route().handler(BodyHandler.create().setUploadsDirectory(System.getProperty("java.io.tmpdir")));
      Handler<RoutingContext> echoApi = routingContext -> {
         routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.TEXT_HTML).putHeader(HttpHeaders.CONTENT_LENGTH, RESPONSE_LENGTH).end(RESPONSE_TEXT.toString());
      };
      router.route("/echo-api/*").handler(echoApi);
      router.route("/buddhi-mock").handler(routingContext -> {
         routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, TEXT_CSV).putHeader(HttpHeaders.CONTENT_LENGTH, CSV_RESPONSE_LENGTH).end(CSV);
      });
      router.post(HOSTS_URL).handler(hosts());
      router.post(HYPERFOIL_CONFIG_URL).handler(processHyperfoilConfig());
      router.route("/*").handler(echoApi);
      vertx.createHttpServer().requestHandler(router::accept).listen(SERVICE_PORT);
   }

   /**
    * This handler will read the uploaded file, parse it for the hosts.
    * Returns a single line CSV of hosts to the client.
    * @return a Handler for this request
    */
   private Handler<RoutingContext> hosts()
         throws Exception{
      return routingContext -> {
         routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, TEXT_CSV);
         routingContext.response().setChunked(true);
         Set<String> hosts = new HashSet<>();
         for (FileUpload f : routingContext.fileUploads()) {
            Path path = Paths.get( f.uploadedFileName() );
            try ( BufferedReader reader = Files.newBufferedReader(path) ){
               reader.lines().forEach(
                  line -> {
                     hosts.add(line.split(",")[0].replaceAll("\"",  ""));
                  }
               );
            } catch (IOException ioe) {
               LOGGER.log(Level.SEVERE, ioe.getMessage(), ioe);
            }
            try {
               Files.delete(path);
            } catch (Exception ioe) {
               LOGGER.log(Level.SEVERE, ioe.getMessage()+": cleanup of downloaded file failed.", ioe);
            }
         }
         routingContext.response().write(String.join(",", hosts)).end();
      };
   }

   private Handler<RoutingContext> processHyperfoilConfig() 
         throws Exception{
      return routingContext -> {
         routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
         routingContext.response().setChunked(true);

         String queryParam = routingContext.queryParam(Params.hosts.toString()).get(0);
         List<String> hosts = Arrays.asList(queryParam.split(","));
         String port = routingContext.queryParam(Params.port.toString()).get(0);
         String connections = routingContext.queryParam(Params.connections.toString()).get(0);
         String csv = routingContext.queryParam(Params.csv.toString()).get(0);
         String rampUpUsers = routingContext.queryParam(Params.rampUpUsers.toString()).get(0);
         String steadyStateUsers = routingContext.queryParam(Params.steadyStateUsers.toString()).get(0);
         String rampUpDuration = routingContext.queryParam(Params.rampUpDuration.toString()).get(0);
         String steadyStateDuration = routingContext.queryParam(Params.steadyStateDuration.toString()).get(0);
         String rampDownDuration = routingContext.queryParam(Params.rampDownDuration.toString()).get(0);

         Function<String, String> replace = (line -> {
            if (line.contains(Params.hosts.TEXT)) {
               StringBuilder builder = new StringBuilder();
               hosts.forEach(s ->  builder
                     .append(String.format("- host: http://%1$s:%2$s", s, port)).append(LS)
                     .append(String.format("  sharedConnections: %1$s", connections)).append(LS)
               );
               return builder.toString();
            } else if (line.contains(Params.csv.TEXT)) {
               return String.format("             file: %1$s%2$s", csv, LS);
            } else if (line.contains(Params.pattern.TEXT)) {
             return String.format("              pattern: ${target-host}:%1$s%2$s", port, LS);
            } else if (line.contains(Params.rampUpUsers.TEXT)) {
               return String.format("      targetUsersPerSec: %1$s%2$s", rampUpUsers, LS);
            } else if (line.contains(Params.steadyStateUsers.TEXT)) {
               return String.format("      usersPerSec: %1$s%2$s", steadyStateUsers, LS);
            } else if (line.contains(Params.rampUpDuration.TEXT)) {
               return String.format("      duration: %1$s%2$s", rampUpDuration, LS);
            } else if (line.contains(Params.steadyStateDuration.TEXT)) {
               return String.format("      duration: %1$s%2$s", steadyStateDuration, LS);
            } else if (line.contains(Params.rampDownDuration.TEXT)) {
               return String.format("      duration: %1$s%2$s", rampDownDuration, LS);
            } else {
               return line + LS;
            }
         });
         // assume only one file is uploaded
         String lines = null;
         for (FileUpload f : routingContext.fileUploads()) {
            Path path = Paths.get( f.uploadedFileName() );
            try ( BufferedReader reader = Files.newBufferedReader(path) ){
               lines = reader.lines()
                     .map(replace)
                     .collect(Collectors.joining());
            } catch (IOException ioe) {
               LOGGER.log(Level.SEVERE, ioe.getMessage(), ioe);
            }
            try {
               Files.delete(path);
            } catch (Exception ioe) {
               LOGGER.log(Level.SEVERE, ioe.getMessage()+": cleanup of downloaded file failed.", ioe);
            }
         }
         routingContext.response().write(lines).end();
      };
   }

   public enum Params {
      hosts ( "HOSTS_LIST_HERE"),
      csv ( "CSV_FILE_PATH_HERE"),
      pattern ( "PATTERN_HERE"),
      rampUpUsers ( "RAMP_UP_USERS_HERE"),
      steadyStateUsers ( "STEADY_STATE_USERS_HERE"),
      rampUpDuration ( "RAMP_UP_DURATION_HERE"),
      steadyStateDuration ( "STEADY_STATE_DURATION_HERE"),
      rampDownDuration ( "RAMP_DOWN_DURATION_HERE"),
      port ( "" ),
      connections( "" );

      public String TEXT;

      private Params(String text) {
         this.TEXT= text;
      }
   }
}

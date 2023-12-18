package cfp.api;

import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import cfp.entity.ConferenceEntity;
import cfp.entity.CreateConference;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.impl.action.ActionEffectImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@RequestMapping("/api/slack/conferences")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class ConferenceController extends Action {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    private final ComponentClient componentClient;

    private final Http akkaHttp = Http.get(actionContext().materializer().system());

    public ConferenceController(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @PostMapping("/create")
    public Action.Effect<String> addConference(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String team_id,
            @RequestParam(required = false) String team_domain,
            @RequestParam(required = false) String enterprise_id,
            @RequestParam(required = false) String enterprise_name,
            @RequestParam(required = false) String channel_id,
            @RequestParam(required = false) String channel_name,
            @RequestParam(required = false) String user_id,
            @RequestParam(required = false) String user_name,
            @RequestParam(required = false) String command,
            @RequestParam String text,
            @RequestParam(required = false) String response_url,
            @RequestParam(required = false) String trigger_id,
            @RequestParam(required = false) String api_app_id
    ) {

        logger.info("Adding conference, token: " + token + ", team_id: " + team_id + ", team_domain: " + team_domain + ", enterprise_id: " + enterprise_id + ", enterprise_name: " + enterprise_name + ", channel_id: " + channel_id + ", channel_name: " + channel_name + ", user_id: " + user_id + ", user_name: " + user_name + ", command: " + command + ", text: " + text + ", response_url: " + response_url + ", trigger_id: " + trigger_id + ", api_app_id: " + api_app_id);

        var conferenceId = UUID.randomUUID().toString();
        CreateConference createConference = parseCreateConference(text);

        var call = componentClient
                .forValueEntity(conferenceId)
                .call(ConferenceEntity::createConference)
                .params(conferenceId, createConference)
                .execute();

        // transform response
        CompletionStage<Effect<String>> effect =
                call.handle((conference, error) -> {
                    if (error == null) {
                        logger.info("Created conference: " + conference);
                        return effects().reply(conference.id());
                    } else {
                        logger.error("Failed to create conference", error);
                        return effects().error("Failed to create conference, please retry");
                    }
                });

        return effects().asyncEffect(effect);
    }

    private CreateConference parseCreateConference(String text) {
        String[] split = text.split(" ");
        String name = split[0];
        String deadline = split[1];
        return new CreateConference(name, LocalDate.parse(deadline));
    }

    /**
     * When called, this posts the whole list of conferences to the #cfp-hackathon channel.
     */
    @PostMapping("/post-list")
    public Effect<String> postList() {
        CompletionStage<ConferenceViewList> conferences = componentClient
                .forView()
                .call(ListConferenceView::getConferences)
                .execute();

        CompletionStage<String> resCompletionStage = conferences.thenCompose(list -> {
            var markdown = list.list().stream()
                    .map(cv -> "* " + cv.name() + " " + cv.deadline() + "\n")
                    .collect(Collectors.joining());
            var req = HttpRequest.POST("https://hooks.slack.com/services/...")
                    .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON,
                            "{\"text\":\"" + markdown + "\", \"mrkdwn\":true }"));
            return akkaHttp.singleRequest(req);
        }).thenApply(res -> {
            switch (res.status().intValue()) {
                case 200:
                    return "OK";
                default:
                    if (res.entity().isStrict()) {
                        final HttpEntity.Strict strict = (HttpEntity.Strict)res.entity();
                        logger.error("request failed with: " + strict.getData().utf8String());
                    } else {
                        logger.error("request failed: status=" + res.status().intValue());
                    }
                    return "failed";
            }
        });
        return effects().asyncReply(resCompletionStage);
    }

}

package cfp.action;

import cfp.entity.ConferenceEntity;
import cfp.entity.CreateConference;
import kalix.javasdk.action.Action;

import kalix.javasdk.client.ComponentClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@RequestMapping("/api/slack/conferences")
public class ConferenceController extends Action {

    private final ComponentClient componentClient;

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
        System.out.println("token: " + token + ", team_id: " + team_id + ", team_domain: " + team_domain + ", enterprise_id: " + enterprise_id + ", enterprise_name: " + enterprise_name + ", channel_id: " + channel_id + ", channel_name: " + channel_name + ", user_id: " + user_id + ", user_name: " + user_name + ", command: " + command + ", text: " + text + ", response_url: " + response_url + ", trigger_id: " + trigger_id + ", api_app_id: " + api_app_id);

        var conferenceId = UUID.randomUUID().toString();
        CreateConference createConference = parseCreateConference(text);

        var call = componentClient
                .forValueEntity(conferenceId)
                .call(ConferenceEntity::createConference)
                .params(conferenceId, createConference)
                .execute();

        // transform response
        CompletionStage<Effect<String>> effect =
                call.handle((empty, error) -> {
                    if (error == null) {
                        return effects().reply(conferenceId);
                    } else {
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


    //
//    @PostMapping("/println")
//    public Effect<String> println() {
//        componentClient.forView() forValueEntity(conferenceId).call(ConferenceEntity::createConference).params(conferenceId,
//                createConference);
//        System.out.println("asfd");
//        return effects().reply("");
//    }

}

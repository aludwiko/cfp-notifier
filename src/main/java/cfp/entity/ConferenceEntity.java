package cfp.entity;

import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@TypeId("conference")
@Id("conferenceId")
public class ConferenceEntity extends ValueEntity<Conference> {

    @PostMapping("/conferences/{conferenceId}")
    public Effect<Conference> createConference(
            @PathVariable String conferenceId, @RequestBody CreateConference createConference
    ) {

        var conference = new Conference(
                conferenceId,
                createConference.name(),
                createConference.deadline()
        );

        return effects()
                .updateState(conference)
                .thenReply(conference);
    }

    @GetMapping("/conferences/{conferenceId}")
    public Effect<Conference> getConferenceById() {
        return effects()
                .reply(currentState());
    }
}
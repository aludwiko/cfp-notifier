package cfp.api;

import cfp.entity.Conference;
import cfp.entity.ConferenceEntity;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.view.View;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;


@Table("conferences")
public class ListConferenceView extends View<ConferenceView> {

    @Subscribe.ValueEntity(ConferenceEntity.class)
    public UpdateEffect<ConferenceView> onChange(Conference conference) {
        return effects()
                .updateState(new ConferenceView(conference.id(), conference.name(), conference.deadline().toString()));
    }
    @GetMapping("/conferences")
    @Query("SELECT * FROM conferences")
    public Flux<ConferenceView> getConference() {
        return null;
    }

}

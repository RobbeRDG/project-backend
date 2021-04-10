package be.kul.rideservice.entity;

import be.kul.rideservice.utils.json.jsonObjects.amqpMessages.ride.RideWaypoint;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import org.n52.jackson.datatype.jts.GeometryDeserializer;
import org.n52.jackson.datatype.jts.GeometrySerializer;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;
import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
public class WayPoint {
    @NotNull
    private Timestamp time;
    @NotNull
    @JsonSerialize( using = GeometrySerializer.class)
    @JsonDeserialize( contentUsing = GeometryDeserializer.class)
    private Point location;

    @JsonIgnore
    @ManyToOne(cascade = CascadeType.PERSIST)
    private Ride ride;

    public WayPoint(RideWaypoint rideWaypoint, Ride ride) {
        this.time = rideWaypoint.getTime();
        this.location = rideWaypoint.getLocation();
        this.ride = ride;
    }

    public Timestamp getTime() {
        return time;
    }
}

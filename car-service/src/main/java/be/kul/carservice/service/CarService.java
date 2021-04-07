package be.kul.carservice.service;

import be.kul.carservice.controller.amqp.AmqpProducerController;
import be.kul.carservice.entity.Car;
import be.kul.carservice.entity.Reservation;
import be.kul.carservice.entity.Ride;
import be.kul.carservice.repository.CarRepository;
import be.kul.carservice.repository.ReservationRepository;
import be.kul.carservice.repository.RideRepository;
import be.kul.carservice.utils.exceptions.*;
import be.kul.carservice.utils.helperObjects.RideState;
import be.kul.carservice.utils.json.jsonObjects.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.List;

@org.springframework.stereotype.Service
public class CarService {
    public static final Logger logger = LoggerFactory.getLogger(CarService.class);

    private static final int RESERVATION_COOLDOWN_IN_MINUTES = 120;

    @Autowired
    private AmqpProducerController amqpProducerController;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RideRepository rideRepository;


    public Car registerCar(Car car) {
        //First check if the car doesn't already exist
        if (carRepository.existsByNumberPlate(car.getNumberPlate())) {
            String errorMessage = "Car with numberplate '" + car.getNumberPlate() + "' already exists";
            logger.warn(errorMessage);
            throw new AlreadyExistsException(errorMessage);
        }

        //Set the latestStateUpdate to now
        car.setLastStateUpdateTimestamp(new Timestamp(System.currentTimeMillis()));
        logger.info("Car with numberplate '" +car.getNumberPlate() + "' created");
        return carRepository.save(car);
    }

    public List<Car> findAllCarsWithinRadius(Double longitude, double latitude, double radiusInKM) {
        return carRepository.findAllCarsWithinRadius(longitude, latitude, radiusInKM);
    }

    public List<Car> registerCars(List<Car> carList) {
        //First check if any of the cars don't already exist
        StringBuilder errorMessage = new StringBuilder("Car(s) with numberplate: ");
        boolean error = false;
        for(Car car: carList) {
            if (carRepository.existsByNumberPlate(car.getNumberPlate())) {
                error = true;
                errorMessage.append(" '" + car.getNumberPlate() + "' ");
            }
        }
        errorMessage.append("Already exist");
        if (error) {
            logger.warn(errorMessage.toString());
            throw new AlreadyExistsException(errorMessage.toString());
        }

        //Set the latestStateUpdate of each car to now
        Timestamp now = new Timestamp(System.currentTimeMillis());
        for(Car car: carList) {
            car.setLastStateUpdateTimestamp(now);
        }

        logger.info("Multiple cars created");
        return carRepository.saveAll(carList);
    }

    public List<Car> findAllAvailableCarsWithinRadius(double longitude, double latitude, double radiusInKM) {
        return carRepository.findAllAvailableCarsWithinRadius(longitude, latitude, radiusInKM);
    }

    public List<Car> findAllMaintenanceRequiringCarsWithinRadius(double longitude, double latitude, double radiusInKM) {
        return carRepository.findAllMaintenanceRequiringCarsWithinRadius(longitude, latitude, radiusInKM);
    }


    public Car updateCarState(CarStateUpdate stateUpdate) {
        long carId = stateUpdate.getCarId();
        Car car = carRepository.findById(carId).orElse(null);
        if (car==null) throw new DoesntExistException("The car with id '" + carId + "' doesn't exist");

        //Update parameters
        car.setRemainingFuelInKilometers(stateUpdate.getRemainingFuelInKilometers());
        car.setLocation(stateUpdate.getLocation());
        car.setOnline(stateUpdate.isOnline());
        car.setLastStateUpdateTimestamp(stateUpdate.getCreatedOn());
        logger.info("Updated car state of car with id '" + carId + "'");

        return carRepository.save(car);
    }


    @Transactional(dontRollbackOn = {NotAvailableException.class, DoesntExistException.class, ReservationCooldownException.class})
    public Car reserveCar(String userId, long id) {
        //Get the requested car
        Car car = carRepository.findById(id).orElse(null);
        if (car==null) throw new DoesntExistException("Couldn't reserve car: The car with id '" + id + " doesn't exist");

        //check if the car is not in use or in maintenance or reserved
        if (!car.canBeReserved()) throw new NotAvailableException("Couldn't reserve car: The car with id '" + id + "' can't be reserved now");

        //Check if the user can place a reservation
        if(isUserOnCooldown(userId, RESERVATION_COOLDOWN_IN_MINUTES)) throw new ReservationCooldownException("Couldn't reserve car: User is still on cooldown");

        //Create a new reservation
        Reservation reservation = new Reservation(userId, car);

        //Save the reservation
        reservationRepository.save(reservation);

        //Set the reservation state of the car
        car.setCurrentReservation(reservation);

        //Log the reservation
        logger.info("Placed reservation on car with id '" + id + "'");

        //Save the new state
        return carRepository.save(car);
    }

    public Reservation registerReservation(Reservation reservation) {
        return reservationRepository.save(reservation);
    }



    public boolean isReserved(long carId) {
        Reservation mostRecent =  reservationRepository.getMostRecentReservationForCar(carId).orElse(null);

        //If there are no reservations on the car
        if (mostRecent==null) return false;

        //Check if the most recent reservation has already expired
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        Timestamp validUntil = mostRecent.getValidUntil();
        if(validUntil.before(currentTime)) return false;
        else return true;
    }

    public boolean isUserOnCooldown(String userId, int reservationCooldownInMinutes) {
        Reservation mostRecent =  reservationRepository.getMostRecentReservationFromUser(userId).orElse(null);
        if (mostRecent==null) return false;

        Timestamp currentTimeMinusCooldown = new Timestamp(System.currentTimeMillis() - reservationCooldownInMinutes*60*1000);
        if (mostRecent.getCreatedOn().before(currentTimeMinusCooldown)) return false;
        return true;
    }

    public Car setCarToActive(long carId) {
        //Get the requested car
        Car car = carRepository.findById(carId).orElse(null);
        if (car==null) throw new DoesntExistException("Couldn't set car to active: The car with id '" + carId + " doesn't exist");

        //Set the car to active state
        car.setActive(true);

        //Save and return the car
        return carRepository.save(car);
    }

    public Car setCarToInactive(long carId) {
        //Get the requested car
        Car car = carRepository.findById(carId).orElse(null);
        if (car==null) throw new DoesntExistException("Couldn't set car to inactive: The car with id '" + carId + " doesn't exist");

        //Set the car to active state
        car.setActive(false);

        //Save and return the car
        return carRepository.save(car);
    }

    public Car rideCar(String userId, long carId) throws Exception {
        //Get the requested car
        Car car = carRepository.findById(carId).orElse(null);
        if (car==null) throw new DoesntExistException("Couldn't start ride: The car with id '" + carId + " doesn't exist");

        //check if the car can be ridden
        if (!car.canBeRidden(userId)) throw new NotAvailableException(
                "Couldn't start ride: The car with id '" + carId + "' can't be ridden now");

        //Create a new ride
        Ride ride = new Ride(userId, car);

        //Save the new ride
        rideRepository.save(ride);

        //Send a ride request to the car and wait for response
        CarRideRequest rideRequest = new CarRideRequest(ride);
        CarAcknowledgement ack;
        try {
            ack = amqpProducerController.sendCarRideRequest(rideRequest);
        } catch(CarOfflineException e) {
            handleCarOfflineException(carId);
            throw e;
        }

        //Check if the acknowledgement confirms the ride request
        if(ack.confirmsRideRequest(rideRequest)) throw new NotAvailableException(
                "Couldn't start ride: The car with id '" + carId + "' can't be ridden now");

        //Set the ride state to IN_PROGRESS
        ride.setCurrentState(RideState.IN_PROGRESS);

        //Set the new car state
        car.setCurrentRide(ride);

        //Send a RideInitialisation to the ride service
        RideInitialisation rideInitialisation = new RideInitialisation(ride);
        amqpProducerController.sendRideInitialisation(rideInitialisation);

        //Return the new car state
        return carRepository.save(car);
    }

    private void handleCarOfflineException(long carId) throws Exception {
        //Get the not-responding car
        Car car = carRepository.findById(carId).orElse(null);
        if (car==null) throw new Exception("Couldn't handle offlineException: The car with id '" + carId + " doesn't exist");

        //Set the new state not-responding car
        car.setOnline(false);
        car.setLastStateUpdateTimestamp(new Timestamp(System.currentTimeMillis()));

        //Save the car state
        carRepository.save(car);
    }

    public ResponseEntity<Object> lockCar(String userId, long carId,boolean lock) throws JsonProcessingException {
        //Get the requested car
        Car car = carRepository.findById(carId).orElse(null);
        if (car==null) throw new DoesntExistException("Couldn't lock car: The car with id '" + carId + " doesn't exist");

        //Check if the user is currently riding the requested car
        if (!car.getCurrentRide().getUserId().equals(userId)) throw new NotAllowedException(
                "Couldn't lock/unlock car: Cars can only be locked when the user is currently riding the car");

        //Create a CarLockRequest
        CarLockRequest carLockRequest = new CarLockRequest(car.getCurrentRide().getRideId(), carId, true);

        //Send the request to the car
        CarAcknowledgement ack = amqpProducerController.sendCarLockRequest(carLockRequest);

        //Check if the acknowledgement confirms the lockrequest
        if(ack.confirmsLockRequest(carLockRequest)) throw new NotAvailableException(
                "Couldn't lock/unlock car: The car with id '" + carId + "' can't be locked/unlocked now");

        //Return to client
        if (lock) return ResponseEntity.ok().body("car is locked");
        return ResponseEntity.ok().body("car is unlocked");
    }
}

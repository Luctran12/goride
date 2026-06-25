package com.example.goride.matching.notification;

public interface DriverOfferNotifier {
    void notifyDriver(Long driverId, DriverOfferNotification notification);

    void notifyOfferCancelled(Long driverId, DriverOfferCancelledNotification notification);
}

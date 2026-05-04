package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.RoomClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class RoomHelper {

    private final RoomClient roomClient;

    public RoomHelper(RoomClient roomClient) {
        this.roomClient = roomClient;
    }

    public void validateRoomExists(Long bedId) {
        try {
            roomClient.existsRoom(bedId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Room not found: " + bedId,
                    "room",
                    "notfound"
            );
        }
    }

}

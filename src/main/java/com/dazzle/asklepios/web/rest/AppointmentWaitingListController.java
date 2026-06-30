package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.AppointmentWaitingListBookingService;
import com.dazzle.asklepios.service.AppointmentWaitingListService;
import com.dazzle.asklepios.service.dto.appointmentWaitingList.AppointmentWaitingListBookDTO;
import com.dazzle.asklepios.service.dto.appointmentWaitingList.AppointmentWaitingListCreateDTO;
import com.dazzle.asklepios.service.dto.appointmentWaitingList.AppointmentWaitingListRemoveDTO;
import com.dazzle.asklepios.web.rest.vm.appointmentWaitingList.AppointmentWaitingListVM;
import com.dazzle.asklepios.web.rest.vm.appointmentWaitingList.WaitingListAvailableSlotsByBookingModeVM;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class AppointmentWaitingListController {

    private final AppointmentWaitingListService waitingListService;
    private final AppointmentWaitingListBookingService bookingService;

    public AppointmentWaitingListController(AppointmentWaitingListService waitingListService, AppointmentWaitingListBookingService bookingService) {
        this.waitingListService = waitingListService;
        this.bookingService = bookingService;
    }

    @PostMapping("/waiting-list")
    public ResponseEntity<AppointmentWaitingListVM> create(@Valid @RequestBody AppointmentWaitingListCreateDTO dto) {
        return ResponseEntity.ok(waitingListService.create(dto));
    }

    @GetMapping("/waiting-list")
    public ResponseEntity<List<AppointmentWaitingListVM>> getWaiting(@RequestParam Long facilityId, @RequestParam Long departmentId) {
        return ResponseEntity.ok(waitingListService.getWaiting(facilityId, departmentId));
    }

    @GetMapping("/waiting-list/{id}/available-slots")
    public ResponseEntity<WaitingListAvailableSlotsByBookingModeVM> getAvailableSlots(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate preferredDate
    ) {
        return ResponseEntity.ok(waitingListService.findAvailableSlots(id, preferredDate));
    }

    @PostMapping("/waiting-list/{id}/book")
    public ResponseEntity<AppointmentWaitingListVM> book(@PathVariable Long id, @Valid @RequestBody AppointmentWaitingListBookDTO dto) {
        return ResponseEntity.ok(bookingService.book(id, dto));
    }

    @PostMapping("/waiting-list/{id}/remove")
    public ResponseEntity<AppointmentWaitingListVM> removeFromWaitingList(@PathVariable Long id, @RequestBody(required = false) AppointmentWaitingListRemoveDTO dto) {
        return ResponseEntity.ok(waitingListService.removeFromWaitingList(id, dto));
    }
}
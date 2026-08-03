package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "appointment_waiting_list_booking")
@Getter
@Setter
public class AppointmentWaitingListBooking implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiting_list_id", nullable = false)
    private AppointmentWaitingList waitingList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_group_id", nullable = false)
    private AppointmentBookingGroup bookingGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "start_datetime", nullable = false)
    private Instant startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private Instant endDatetime;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_status_before_booking", nullable = false)
    private AppointmentStatus sourceStatusBeforeBooking;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_mode", nullable = false)
    private BookingMode bookingMode;
}
package com.dazzle.asklepios.web.rest.vm.patient;

public record PortalAppUpdateNotificationVM(
        int portalPatientCount,
        int notifiedCount
) {
}

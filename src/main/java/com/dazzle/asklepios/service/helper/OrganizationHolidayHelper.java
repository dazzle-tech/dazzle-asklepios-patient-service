package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.OrganizationHolidayClient;
import com.dazzle.asklepios.client.setup.dto.OrganizationHolidayDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class OrganizationHolidayHelper {
    private final OrganizationHolidayClient organizationHolidayClient;
    public OrganizationHolidayHelper(OrganizationHolidayClient organizationHolidayClient) {
        this.organizationHolidayClient = organizationHolidayClient;
    }

    public List<OrganizationHolidayDTO> getOrganizationHolidayByDateRange(Long facilityId, LocalDate fromDate, LocalDate toDate) {
        if(facilityId == null ){
            throw new BadRequestAlertException(
                    "Facility are required",
                    "organizationHoliday",
                    "datenull"
            );
        }

        if (fromDate == null || toDate == null) {
            throw new BadRequestAlertException(
                    "From date and to date are required",
                    "organizationHoliday",
                    "datenull"
            );
        }

        if (toDate.isBefore(fromDate)) {
            throw new BadRequestAlertException(
                    "To date cannot be before from date",
                    "organizationHoliday",
                    "dateinvalid"
            );
        }
      return organizationHolidayClient.getActiveHolidaysInRange(fromDate.toString(), toDate.toString(), facilityId);
    }

}

package com.dazzle.asklepios.repository.projection;

public interface EncounterVaccinationProjections {

    interface VaccineIdView {
        Long getVaccineId();
    }

    interface VaccineBrandIdView {
        Long getVaccineBrandId();
    }

    interface VaccineDoseIdView {
        Long getVaccineDoseId();
    }
}

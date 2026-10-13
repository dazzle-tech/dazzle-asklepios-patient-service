package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DentalChartProgressNote;
import com.dazzle.asklepios.repository.DentalChartProgressNoteRepository;
import com.dazzle.asklepios.service.dto.dentalChart.ProgressNoteDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ProgressNoteSaveDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class DentalChartProgressNoteService {

    private final DentalChartProgressNoteRepository dentalChartProgressNoteRepository;

    public DentalChartProgressNoteService(DentalChartProgressNoteRepository dentalChartProgressNoteRepository) {
        this.dentalChartProgressNoteRepository = dentalChartProgressNoteRepository;
    }

    public List<ProgressNoteDTO> saveNotes(Long chartId, List<ProgressNoteSaveDTO> notes) {
        if (notes != null) {
            for (ProgressNoteSaveDTO noteDto : notes) {
                boolean blank = noteDto.note() == null || noteDto.note().trim().isEmpty();

                if (noteDto.id() == null) {
                    if (!blank) {
                        DentalChartProgressNote entity = DentalChartProgressNote.builder()
                                .chartId(chartId)
                                .note(noteDto.note())
                                .build();
                        dentalChartProgressNoteRepository.save(entity);
                    }
                } else {
                    DentalChartProgressNote entity = dentalChartProgressNoteRepository.findById(noteDto.id())
                            .orElseThrow(() -> new NotFoundAlertException(
                                    "Progress note not found: " + noteDto.id(),
                                    "dentalChartProgressNote",
                                    "notfound"
                            ));

                    if (blank) {
                        if (entity.getDeletedAt() == null) {
                            entity.setDeletedAt(Instant.now());
                            dentalChartProgressNoteRepository.save(entity);
                        }
                    } else if (!Objects.equals(entity.getNote(), noteDto.note())) {
                        entity.setNote(noteDto.note());
                        dentalChartProgressNoteRepository.save(entity);
                    }
                }
            }
        }

        return dentalChartProgressNoteRepository
                .findByChartIdAndDeletedAtIsNullOrderByCreatedDateDesc(chartId)
                .stream()
                .map(ProgressNoteDTO::fromEntity)
                .toList();
    }

    public List<ProgressNoteDTO> getNotesForChart(Long chartId) {
        return dentalChartProgressNoteRepository
                .findByChartIdAndDeletedAtIsNullOrderByCreatedDateDesc(chartId)
                .stream()
                .map(ProgressNoteDTO::fromEntity)
                .toList();
    }

    public void deleteNote(Long noteId) {
        DentalChartProgressNote entity = dentalChartProgressNoteRepository.findById(noteId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Progress note not found: " + noteId,
                        "dentalChartProgressNote",
                        "notfound"
                ));

        if (entity.getDeletedAt() == null) {
            entity.setDeletedAt(Instant.now());
            dentalChartProgressNoteRepository.save(entity);
        }
    }
}

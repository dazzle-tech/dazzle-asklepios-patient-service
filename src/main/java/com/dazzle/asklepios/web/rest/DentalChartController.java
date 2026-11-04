package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.DentalChartProgressNoteService;
import com.dazzle.asklepios.service.DentalChartService;
import com.dazzle.asklepios.service.ToothActionService;
import com.dazzle.asklepios.service.ToothServiceAttachmentService;
import com.dazzle.asklepios.service.dto.dentalChart.ChartUpdateDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalChartResponseDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ProgressNoteDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ProgressNoteSaveDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothActionCancelDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothActionSaveDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothServiceCancelDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothServiceSaveDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient/dental")
public class DentalChartController {

    private static final Logger LOG = LoggerFactory.getLogger(DentalChartController.class);

    private final DentalChartService dentalChartService;
    private final ToothActionService toothActionService;
    private final ToothServiceAttachmentService toothServiceAttachmentService;
    private final DentalChartProgressNoteService dentalChartProgressNoteService;

    public DentalChartController(
            DentalChartService dentalChartService,
            ToothActionService toothActionService,
            ToothServiceAttachmentService toothServiceAttachmentService,
            DentalChartProgressNoteService dentalChartProgressNoteService
    ) {
        this.dentalChartService = dentalChartService;
        this.toothActionService = toothActionService;
        this.toothServiceAttachmentService = toothServiceAttachmentService;
        this.dentalChartProgressNoteService = dentalChartProgressNoteService;
    }

    @GetMapping("/charts/by-encounter/{encounterId}")
    public ResponseEntity<DentalChartResponseDTO> getOrCreateChartForEncounter(@PathVariable Long encounterId) {
        LOG.debug("REST get or create dental chart for encounterId={}", encounterId);
        return ResponseEntity.ok(dentalChartService.getOrCreateChartForEncounter(encounterId));
    }

    @GetMapping("/charts/{chartId}")
    public ResponseEntity<DentalChartResponseDTO> getChartById(@PathVariable Long chartId) {
        LOG.debug("REST get dental chart id={}", chartId);
        return ResponseEntity.ok(dentalChartService.getChartById(chartId));
    }

    @PutMapping("/charts/{chartId}")
    public ResponseEntity<DentalChartResponseDTO> updateChart(
            @PathVariable Long chartId,
            @Valid @RequestBody ChartUpdateDTO dto
    ) {
        LOG.debug("REST update dental chart id={} payload={}", chartId, dto);
        return ResponseEntity.ok(dentalChartService.updateChart(chartId, dto));
    }

    @PostMapping("/tooth-actions")
    public ResponseEntity<DentalChartResponseDTO> saveToothAction(@Valid @RequestBody ToothActionSaveDTO dto) {
        LOG.debug("REST save tooth action payload={}", dto);
        return ResponseEntity.ok(toothActionService.saveToothAction(dto));
    }

    @PostMapping("/tooth-actions/{id}/cancel")
    public ResponseEntity<DentalChartResponseDTO> cancelToothAction(
            @PathVariable Long id,
            @Valid @RequestBody ToothActionCancelDTO dto
    ) {
        LOG.debug("REST cancel tooth action id={}", id);
        return ResponseEntity.ok(toothActionService.cancelToothAction(id, dto.cancellationReason()));
    }

    @PostMapping("/tooth-services")
    public ResponseEntity<DentalChartResponseDTO> saveManualToothService(@Valid @RequestBody ToothServiceSaveDTO dto) {
        LOG.debug("REST save manual tooth service payload={}", dto);
        return ResponseEntity.ok(toothServiceAttachmentService.saveManualToothService(dto));
    }

    @PostMapping("/tooth-services/{id}/cancel")
    public ResponseEntity<DentalChartResponseDTO> cancelToothService(
            @PathVariable Long id,
            @Valid @RequestBody ToothServiceCancelDTO dto
    ) {
        LOG.debug("REST cancel tooth service id={}", id);
        return ResponseEntity.ok(
                toothServiceAttachmentService.cancelToothService(id, dto.cancellationReason())
        );
    }

    @GetMapping("/charts/{chartId}/progress-notes")
    public ResponseEntity<List<ProgressNoteDTO>> getProgressNotes(@PathVariable Long chartId) {
        LOG.debug("REST get progress notes chartId={}", chartId);
        return ResponseEntity.ok(dentalChartProgressNoteService.getNotesForChart(chartId));
    }

    @PutMapping("/charts/{chartId}/progress-notes")
    public ResponseEntity<List<ProgressNoteDTO>> saveProgressNotes(
            @PathVariable Long chartId,
            @RequestBody List<ProgressNoteSaveDTO> notes
    ) {
        LOG.debug("REST save progress notes chartId={} count={}", chartId, notes == null ? 0 : notes.size());
        return ResponseEntity.ok(dentalChartProgressNoteService.saveNotes(chartId, notes));
    }

    @DeleteMapping("/progress-notes/{noteId}")
    public ResponseEntity<Void> deleteProgressNote(@PathVariable Long noteId) {
        LOG.debug("REST delete progress note id={}", noteId);
        dentalChartProgressNoteService.deleteNote(noteId);
        return ResponseEntity.noContent().build();
    }
}

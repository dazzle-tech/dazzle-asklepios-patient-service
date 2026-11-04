package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.DialysisFlowReadingService;
import com.dazzle.asklepios.service.dto.DialysisFlowReadingCreateDTO;
import com.dazzle.asklepios.service.dto.DialysisFlowReadingUpdateDTO;
import com.dazzle.asklepios.service.vm.DialysisFlowReadingResponseVM;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient/dialysis-flow-readings")
@RequiredArgsConstructor
public class DialysisFlowReadingController {

    private final DialysisFlowReadingService service;

    @PostMapping
    public ResponseEntity<DialysisFlowReadingResponseVM> create(
            @Valid @RequestBody DialysisFlowReadingCreateDTO dto
    ) {
        return ResponseEntity.ok(service.create(dto));
    }

    @PutMapping
    public ResponseEntity<DialysisFlowReadingResponseVM> update(
            @Valid @RequestBody DialysisFlowReadingUpdateDTO dto
    ) {
        return ResponseEntity.ok(service.update(dto));
    }

    @GetMapping("/by-dialysis-session")
    public ResponseEntity<List<DialysisFlowReadingResponseVM>>
    findByDialysisSession(
            @RequestParam Long dialysisSessionId
    ) {
        return ResponseEntity.ok(
                service.findByDialysisSessionId(dialysisSessionId)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

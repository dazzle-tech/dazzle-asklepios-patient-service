package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.UomGroupClient;
import com.dazzle.asklepios.client.setup.dto.UomGroupRelationDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a medication price from its price base UOM unit to another unit of
 * the same UOM group, using the group's relations.
 */
@Service
public class UomPriceFactorHelper {

    private static final String ENTITY_NAME = "uomPriceFactor";
    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private final UomGroupClient uomGroupClient;

    public UomPriceFactorHelper(UomGroupClient uomGroupClient) {
        this.uomGroupClient = uomGroupClient;
    }

    /**
     * Price base units in one {@code chargedUnitId}, so
     * price per charged unit = price per base unit × factor.
     *
     * A relation {@code from → to} with value r means 1 from = r to. Relations
     * can be used in both directions and chained (BOX → STRIP → TABLET).
     */
    public BigDecimal resolveFactor(Long uomGroupId, Long chargedUnitId, Long priceBaseUnitId) {
        if (chargedUnitId == null || priceBaseUnitId == null) {
            throw new BadRequestAlertException(
                    "UOM unit and the medication price base unit are required.",
                    ENTITY_NAME,
                    "unit.required"
            );
        }

        if (chargedUnitId.equals(priceBaseUnitId)) {
            return BigDecimal.ONE;
        }

        if (uomGroupId == null) {
            throw new BadRequestAlertException(
                    "Medication has no UOM group.",
                    ENTITY_NAME,
                    "uomGroup.required"
            );
        }

        Map<Long, Map<Long, BigDecimal>> edges = buildEdges(uomGroupClient.getRelations(uomGroupId));

        // Breadth-first: amount of each unit reached that equals one charged unit
        Map<Long, BigDecimal> amountOf = new HashMap<>();
        amountOf.put(chargedUnitId, BigDecimal.ONE);
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(chargedUnitId);

        while (!queue.isEmpty()) {
            Long unit = queue.poll();
            BigDecimal amount = amountOf.get(unit);

            for (Map.Entry<Long, BigDecimal> edge : edges.getOrDefault(unit, Map.of()).entrySet()) {
                if (amountOf.containsKey(edge.getKey())) {
                    continue;
                }

                BigDecimal next = amount.multiply(edge.getValue(), PRECISION);
                if (edge.getKey().equals(priceBaseUnitId)) {
                    return next;
                }

                amountOf.put(edge.getKey(), next);
                queue.add(edge.getKey());
            }
        }

        throw new BadRequestAlertException(
                "No UOM relation links the selected unit to the medication price base unit.",
                ENTITY_NAME,
                "relation.notfound"
        );
    }

    private Map<Long, Map<Long, BigDecimal>> buildEdges(List<UomGroupRelationDTO> relations) {
        Map<Long, Map<Long, BigDecimal>> edges = new HashMap<>();

        if (relations == null) {
            return edges;
        }

        for (UomGroupRelationDTO relation : relations) {
            if (relation.fromUnit() == null
                    || relation.toUnit() == null
                    || relation.fromUnit().id() == null
                    || relation.toUnit().id() == null
                    || relation.relation() == null
                    || relation.relation().signum() <= 0) {
                continue;
            }

            Long from = relation.fromUnit().id();
            Long to = relation.toUnit().id();

            edges.computeIfAbsent(from, k -> new HashMap<>()).put(to, relation.relation());
            edges.computeIfAbsent(to, k -> new HashMap<>())
                    .put(from, BigDecimal.ONE.divide(relation.relation(), PRECISION));
        }

        return edges;
    }
}

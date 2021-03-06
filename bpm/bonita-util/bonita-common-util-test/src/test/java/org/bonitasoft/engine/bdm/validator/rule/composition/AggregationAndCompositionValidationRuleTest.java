/**
 * Copyright (C) 2019 Bonitasoft S.A.
 * Bonitasoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation
 * version 2.1 of the License.
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth
 * Floor, Boston, MA 02110-1301, USA.
 **/
package org.bonitasoft.engine.bdm.validator.rule.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bonitasoft.engine.bdm.builder.BusinessObjectBuilder.aBO;
import static org.bonitasoft.engine.bdm.builder.BusinessObjectModelBuilder.aBOM;
import static org.bonitasoft.engine.bdm.builder.FieldBuilder.aCompositionField;
import static org.bonitasoft.engine.bdm.builder.FieldBuilder.anAggregationField;

import org.bonitasoft.engine.bdm.model.BusinessObject;
import org.bonitasoft.engine.bdm.model.BusinessObjectModel;
import org.bonitasoft.engine.bdm.validator.ValidationStatus;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Danila Mazour
 */
public class AggregationAndCompositionValidationRuleTest {

    private AggregationAndCompositionValidationRule rule;

    @Before
    public void setUp() {
        rule = new AggregationAndCompositionValidationRule();
    }

    @Test
    public void shouldDetectAggregationAndComposition() {

        final BusinessObject daughter = aBO("daughter").build();
        final BusinessObject mother = aBO("mother").withField(aCompositionField("daughter", daughter)).build();
        final BusinessObject grandMother = aBO("grandMother").withField(anAggregationField("daughter", daughter))
                .build();
        final BusinessObjectModel bom = aBOM().withBOs(grandMother, mother, daughter).build();
        final ValidationStatus validationStatus = rule.validate(bom);

        assertThat(validationStatus.isOk()).isTrue();
        assertThat(validationStatus.getErrors()).isEmpty();
        assertThat(validationStatus.getWarnings().size()).isEqualTo(1);
        assertThat(validationStatus.getWarnings().get(0)).isEqualTo(
                "The object daughter is referenced both in composition and in aggregation. This may lead to runtime errors and may lead to unpredictable behaviour of the AccessControl configuration.");

    }
}

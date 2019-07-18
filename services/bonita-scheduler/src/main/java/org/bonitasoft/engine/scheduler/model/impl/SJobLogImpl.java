/**
 * Copyright (C) 2015 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
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
package org.bonitasoft.engine.scheduler.model.impl;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.bonitasoft.engine.scheduler.model.SJobLog;

/**
 * @author Celine Souchet
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SJobLogImpl extends SPersistentObjectImpl implements SJobLog {

    private long jobDescriptorId;
    private Long retryNumber = 0L;
    private Long lastUpdateDate;
    private String lastMessage;

    public SJobLogImpl(final long jobDescriptorId) {
        super();
        this.jobDescriptorId = jobDescriptorId;
    }

}
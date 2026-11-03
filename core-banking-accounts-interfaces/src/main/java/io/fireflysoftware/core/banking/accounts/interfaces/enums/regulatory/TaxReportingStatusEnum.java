/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.core.banking.accounts.interfaces.enums.regulatory;

/**
 * Defines the tax reporting status of an account.
 * This affects how the account is reported to tax authorities.
 */
public enum TaxReportingStatusEnum {
    /**
     * Account is reportable for tax purposes
     */
    REPORTABLE,
    
    /**
     * Account is exempt from tax reporting
     */
    EXEMPT,
    
    /**
     * Account is subject to backup withholding
     */
    BACKUP_WITHHOLDING,
    
    /**
     * Account is subject to FATCA reporting
     */
    FATCA_REPORTABLE,
    
    /**
     * Account is subject to CRS reporting
     */
    CRS_REPORTABLE,
    
    /**
     * Account is subject to both FATCA and CRS reporting
     */
    FATCA_CRS_REPORTABLE,
    
    /**
     * Account is pending tax documentation
     */
    PENDING_DOCUMENTATION,
    
    /**
     * Account has incomplete tax information
     */
    INCOMPLETE_INFORMATION,
    
    /**
     * Account is not subject to tax reporting
     */
    NON_REPORTABLE
}

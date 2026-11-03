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
 * Defines the regulatory status of an account.
 * This affects how the account is treated for regulatory compliance purposes.
 */
public enum RegulatoryStatusEnum {
    /**
     * Account is compliant with all regulatory requirements
     */
    COMPLIANT,
    
    /**
     * Account has pending regulatory documentation
     */
    PENDING_DOCUMENTATION,
    
    /**
     * Account is under enhanced due diligence
     */
    ENHANCED_DUE_DILIGENCE,
    
    /**
     * Account is under review for regulatory compliance
     */
    UNDER_REVIEW,
    
    /**
     * Account is subject to special monitoring
     */
    SPECIAL_MONITORING,
    
    /**
     * Account is subject to sanctions screening
     */
    SANCTIONS_SCREENING,
    
    /**
     * Account is subject to PEP (Politically Exposed Person) monitoring
     */
    PEP_MONITORING,
    
    /**
     * Account is subject to high-risk monitoring
     */
    HIGH_RISK_MONITORING,
    
    /**
     * Account is exempt from certain regulatory requirements
     */
    REGULATORY_EXEMPTION,
    
    /**
     * Account is non-compliant with regulatory requirements
     */
    NON_COMPLIANT,
    
    /**
     * Account is restricted due to regulatory issues
     */
    REGULATORY_RESTRICTED
}

/*
 *
 *  * Copyright 2019 Johns Hopkins University
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package edu.jhu.library.pass.deposit.provider.dash;

import org.junit.Test;

import static org.junit.Assert.*;

public class OaiPmhStatusProcessorTest {

    private OaiPmhStatusProcessor underTest;

    @Test
    public void encodeResumptionTokenDate() {
        String token = "10/20/1999";
        String expected = "10%2F20%2F1999";

        assertEquals(expected, OaiPmhStatusProcessor.encode(token));
    }

    @Test
    public void encodeResumptionTokenSpace() {
        String token = " 10/20/1999 ";
        String expected = "%2010%2F20%2F1999%20";

        assertEquals(expected, OaiPmhStatusProcessor.encode(token));
    }
}
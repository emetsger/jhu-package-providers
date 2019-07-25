/*
 * Copyright 2019 Johns Hopkins University
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
package edu.jhu.library.pass.deposit.provider.dash;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
interface OaiRequestProcessor {

    Stream<String> listIdentifiers(Instant from);

    Optional<URL> analyzeRecords(URI submissionUri, Stream<String> oaiRecordIds);

}

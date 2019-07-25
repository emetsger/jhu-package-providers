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

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
class DashUtil {

    /**
     * Accepts a NodeList, and returns a Stream&gt;Node&lt;
     *
     * @param nodeList the NodeList
     * @return the NodeList as a Stream&gt;Node&lt;
     */
    static Stream<Node> asStream(NodeList nodeList) {
        int characteristics = SIZED | ORDERED;
        Stream<Node> nodeStream = stream(new Spliterators.AbstractSpliterator<Node>(nodeList.getLength(), characteristics) {
            int index = 0;

            @Override
            public boolean tryAdvance(Consumer<? super Node> action) {
                if (nodeList.getLength() == index) {
                    return false;
                }

                action.accept(nodeList.item(index++));

                return true;
            }
        }, false);

        return nodeStream;
    }

}

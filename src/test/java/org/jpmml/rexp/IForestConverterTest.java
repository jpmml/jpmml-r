/*
 * Copyright (c) 2016 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.dmg.pmml.FieldName;
import org.jpmml.evaluator.Batch;
import org.junit.Test;

public class IForestConverterTest extends ConverterTest {

	@Test
	public void evaluateIsolationForestAuto() throws Exception {
		evaluateIsolationForest("Auto");
	}

	@Test
	public void evaluateIsolationForestIris() throws Exception {
		evaluateIsolationForest("Iris");
	}

	private void evaluateIsolationForest(String dataset) throws Exception {

		try(Batch batch = createBatch("IForest", dataset)){
			Set<FieldName> ignoredFields = ImmutableSet.of(FieldName.create("rawPathLength"), FieldName.create("normalizedPathLength"));

			evaluate(batch, ignoredFields);
		}
	}
}
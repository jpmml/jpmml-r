/*
 * Copyright (c) 2015 Villu Ruusmann
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
package org.jpmml.rexp.testing;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import com.google.common.primitives.UnsignedLong;
import org.jpmml.converter.testing.Datasets;
import org.jpmml.rexp.RandomForestConverter;
import org.jpmml.rexp.testing.RExpEncoderBatchTest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RandomForestConverterTest extends RExpEncoderBatchTest implements Datasets {

	@Test
	public void selectValues(){
		List<String> values = Arrays.asList("1", "2", "3", "4");

		Predicate<Object> truePredicate = new Predicate<Object>(){

			@Override
			public boolean test(Object value){
				return true;
			}
		};

		assertEquals(Arrays.asList("1", "3", "4"), RandomForestConverter.selectValues(values, truePredicate, 13d, true));
		assertEquals(Arrays.asList("2"), RandomForestConverter.selectValues(values, truePredicate, 13d, false));
	}

	@Test
	public void toUnsignedLong(){
		assertEquals(UnsignedLong.valueOf("13"), RandomForestConverter.toUnsignedLong(13d));

		assertEquals(UnsignedLong.valueOf("18446744071562067968"), RandomForestConverter.toUnsignedLong(-2147483648d));
	}

	@Test
	public void evaluateFormulaAudit() throws Exception {
		evaluate("RandomForestFormula", AUDIT);
	}

	@Test
	public void evaluateCustFormulaAudit() throws Exception {
		evaluate("RandomForestCustFormula", AUDIT);
	}

	@Test
	public void evaluateAudit() throws Exception {
		evaluate("RandomForest", AUDIT);
	}

	@Test
	public void evaluateCaretFormulaAudit() throws Exception {
		evaluate("TrainRandomForestFormula", AUDIT);
	}

	@Test
	public void evaluateCaretAudit() throws Exception {
		evaluate("TrainRandomForest", AUDIT);
	}

	@Test
	public void evaluateFormulaAuto() throws Exception {
		evaluate("RandomForestFormula", AUTO);
	}

	@Test
	public void evaluateCustFormulaAuto() throws Exception {
		evaluate("RandomForestCustFormula", AUTO);
	}

	@Test
	public void evaluateAuto() throws Exception {
		evaluate("RandomForest", AUTO);
	}

	@Test
	public void evaluateCaretFormulaAuto() throws Exception {
		evaluate("TrainRandomForestFormula", AUTO);
	}

	@Test
	public void evaluateCaretAuto() throws Exception {
		evaluate("TrainRandomForest", AUTO);
	}

	@Test
	public void evaluateFormulaIris() throws Exception {
		evaluate("RandomForestFormula", IRIS);
	}

	@Test
	public void evaluateCustFormulaIris() throws Exception {
		evaluate("RandomForestCustFormula", IRIS);
	}

	@Test
	public void evaluateIris() throws Exception {
		evaluate("RandomForest", IRIS);
	}

	@Test
	public void evaluateCaretIris() throws Exception {
		evaluate("TrainRandomForest", IRIS);
	}

	@Test
	public void evaluateCaretFormulaIris() throws Exception {
		evaluate("TrainRandomForestFormula", IRIS);
	}

	@Test
	public void evaluateFormulaWineQuality() throws Exception {
		evaluate("RandomForestFormula", WINE_QUALITY);
	}

	@Test
	public void evaluateWineQuality() throws Exception {
		evaluate("RandomForest", WINE_QUALITY);
	}

	@Test
	public void evaluateFormulaWineColor() throws Exception {
		evaluate("RandomForestFormula", WINE_COLOR);
	}

	@Test
	public void evaluateWineColor() throws Exception {
		evaluate("RandomForest", WINE_COLOR);
	}
}
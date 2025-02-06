/*
 * Copyright (c) 2017 Villu Ruusmann
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RExpUtilTest {

	@Test
	public void getVectorType(){
		assertEquals("logical", RExpUtil.getVectorType(RBooleanVector.class));
		assertEquals("numeric", RExpUtil.getVectorType(RDoubleVector.class));
		assertEquals("integer", RExpUtil.getVectorType(RIntegerVector.class));
		assertEquals("numeric", RExpUtil.getVectorType(RNumberVector.class));
		assertEquals("character", RExpUtil.getVectorType(RStringVector.class));

		assertEquals("vector", RExpUtil.getVectorType(RVector.class));
		assertEquals("non-vector", RExpUtil.getVectorType(RExp.class));
	}

	@Test
	public void makeName(){
		assertEquals("X", RExpUtil.makeName(""));

		assertEquals("A", RExpUtil.makeName("A"));
		assertEquals("X1", RExpUtil.makeName("1"));
		assertEquals(".", RExpUtil.makeName("."));
		assertEquals("X_", RExpUtil.makeName("_"));
		assertEquals("X.", RExpUtil.makeName("-"));

		assertEquals("AA", RExpUtil.makeName("AA"));
		assertEquals("A.", RExpUtil.makeName("A."));
		assertEquals("A1", RExpUtil.makeName("A1"));
		assertEquals("A_", RExpUtil.makeName("A_"));
		assertEquals("A.", RExpUtil.makeName("A-"));
	}
}
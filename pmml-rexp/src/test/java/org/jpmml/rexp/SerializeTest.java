/*
 * Copyright (c) 2024 Villu Ruusmann
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.jpmml.model.DirectByteArrayOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SerializeTest {

	@Test
	public void rdsRealVector() throws IOException {
		RDoubleVector realVec = (RDoubleVector)parse("RealVector");

		checkRealVector(rdsClone(realVec));
		checkRealVector(rdsClone(realVec, true));
	}

	@Test
	public void rdsIntegerVector() throws IOException {
		RIntegerVector integerVec = (RIntegerVector)parse("IntegerVector");

		checkIntegerVector(rdsClone(integerVec));
		checkIntegerVector(rdsClone(integerVec, true));
	}

	@Test
	public void rdsNamedList() throws IOException {
		RGenericVector namedList = (RGenericVector)parse("NamedList");

		checkNamedList(rdsClone(namedList));
		checkNamedList(rdsClone(namedList, true));
	}

	@Test
	public void rdsDataFrame() throws IOException {
		RGenericVector dataFrame = (RGenericVector)parse("DataFrame");

		checkDataFrame(rdsClone(dataFrame));
		checkDataFrame(rdsClone(dataFrame, true));
	}

	static
	private void checkRealVector(RDoubleVector realVec){
		assertEquals(5 - 1, realVec.size());

		assertEquals(Arrays.asList(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN), realVec.getValues());
	}

	static
	private void checkIntegerVector(RIntegerVector integerVec){
		assertEquals(3 - 1, integerVec.size());

		assertEquals(Arrays.asList(null, null), integerVec.getValues());
	}

	static
	private void checkNamedList(RGenericVector namedList){
		assertFalse(namedList.hasAttribute("class"));

		assertEquals(10, namedList.size());

		assertEquals((Double)1d, (namedList.getDoubleElement("real")).asScalar());
		assertEquals(Arrays.asList(-1d, 0d, 1d), (namedList.getDoubleElement("real_vector")).getValues());

		assertEquals((Integer)1, (namedList.getIntegerElement("integer").asScalar()));
		assertEquals(Arrays.asList(-1, 0, 1), (namedList.getIntegerElement("integer_vector")).getValues());

		assertEquals(Boolean.TRUE, (namedList.getBooleanElement("logical")).asScalar());
		assertEquals(Arrays.asList(Boolean.FALSE, Boolean.TRUE), (namedList.getBooleanElement("logical_vector")).getValues());

		assertEquals("alpha", (namedList.getStringElement("string")).asScalar());
		assertEquals(Arrays.asList("alpha", "beta", "gamma"), (namedList.getStringElement("string_vector")).getValues());

		assertEquals((Integer)1, (namedList.getFactorElement("factor")).asScalar());
		assertEquals(Arrays.asList(1, 2, 3), (namedList.getFactorElement("factor_vector")).getValues());
		assertEquals(Arrays.asList("alpha", "beta", "gamma"), (namedList.getFactorElement("factor_vector")).getFactorValues());
	}

	static
	private void checkDataFrame(RGenericVector dataFrame){
		assertEquals(Arrays.asList("data.frame"), (dataFrame.getStringAttribute("class")).getValues());

		assertEquals(5, dataFrame.size());

		assertEquals(Arrays.asList(-1d, 0d, 1d), (dataFrame.getDoubleElement("real")).getValues());
		assertEquals(Arrays.asList(-1, 0, 1), (dataFrame.getIntegerElement("integer")).getValues());
		assertEquals(Arrays.asList(Boolean.FALSE, null, Boolean.TRUE), (dataFrame.getBooleanElement("logical")).getValues());
		assertEquals(Arrays.asList("alpha", "beta", "gamma"), (dataFrame.getStringElement("string")).getValues());
		assertEquals(Arrays.asList(1, 2, 3), (dataFrame.getFactorElement("factor")).getValues());
	}

	static
	private <E extends RExp> E rdsClone(E rexp) throws IOException {
		return rdsClone(rexp, false);
	}

	static
	private <E extends RExp> E rdsClone(E rexp, boolean ascii) throws IOException {
		DirectByteArrayOutputStream buffer = new DirectByteArrayOutputStream(10 * 1024);

		try(OutputStream os = buffer){
			write(rexp, os, ascii);
		} // End try

		try(InputStream is = buffer.getInputStream()){
			return (E)parse(is);
		}
	}

	static
	private RExp parse(String name) throws IOException {

		try(InputStream is = SerializeTest.class.getResourceAsStream("/rds/" + name + ".rds")){
			return parse(is);
		}
	}

	static
	private RExp parse(InputStream is) throws IOException {

		try(RExpParser parser = new RExpParser(is)){
			return parser.parse();
		}
	}

	static
	private void write(RExp rexp, OutputStream os, boolean ascii) throws IOException {

		try(RExpWriter writer = new RExpWriter(os, ascii)){
			writer.write(rexp);
		}
	}
}
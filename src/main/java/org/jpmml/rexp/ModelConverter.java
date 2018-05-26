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

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.VerificationField;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;

abstract
public class ModelConverter<R extends RExp> extends Converter<R> {

	public ModelConverter(R object){
		super(object);
	}

	abstract
	public void encodeSchema(RExpEncoder encoder);

	abstract
	public Model encodeModel(Schema schema);

	@Override
	public PMML encodePMML(RExpEncoder encoder){
		RExp object = getObject();

		RGenericVector verification = null;

		if(object instanceof S4Object){
			S4Object model = (S4Object)object;

			verification = (RGenericVector)model.getAttributeValue("verification", true);
		} else

		if(object instanceof RGenericVector){
			RGenericVector model = (RGenericVector)object;

			verification = (RGenericVector)model.getValue("verification", true);
		}

		encodeSchema(encoder);

		Schema schema = encoder.createSchema();

		Model model = encodeModel(schema);

		verification:
		if(verification != null){
			RDoubleVector precision = (RDoubleVector)verification.getValue("precision");
			RDoubleVector zeroThreshold = (RDoubleVector)verification.getValue("zeroThreshold");

			VerificationMap data = new VerificationMap(precision.asScalar(), zeroThreshold.asScalar());

			RGenericVector activeValues = (RGenericVector)verification.getValue("active_values");
			RGenericVector targetValues = (RGenericVector)verification.getValue("target_values", true);
			RGenericVector outputValues = (RGenericVector)verification.getValue("output_values", true);

			if(activeValues != null){
				data.putInputData(encodeVerificationData(activeValues));
			} // End if

			if(targetValues != null && outputValues == null){
				Label label = schema.getLabel();

				FieldName name = label.getName();

				Collection<VerificationField> verificationFields = data.keySet();
				for(Iterator<VerificationField> verificationFieldIt = verificationFields.iterator(); verificationFieldIt.hasNext(); ){
					VerificationField verificationField = verificationFieldIt.next();

					if((verificationField.getField()).equals(name)){
						verificationFieldIt.remove();
					}
				}

				data.putResultData(encodeVerificationData(targetValues, Collections.singletonList(name.getValue())));
			} else

			if(outputValues != null){
				data.putResultData(encodeVerificationData(outputValues));
			} else

			{
				break verification;
			}

			model.setModelVerification(ModelUtil.createModelVerification(data));
		}

		PMML pmml = encoder.encodePMML(model);

		return pmml;
	}

	static
	private Map<VerificationField, List<?>> encodeVerificationData(RGenericVector dataFrame){
		RStringVector names = dataFrame.names();

		return encodeVerificationData(dataFrame, names.getDequotedValues());
	}

	static
	private Map<VerificationField, List<?>> encodeVerificationData(RGenericVector dataFrame, List<String> names){
		Map<VerificationField, List<?>> result = new LinkedHashMap<>();

		for(int i = 0; i < dataFrame.size(); i++){
			String name = names.get(i);
			RVector<?> column = (RVector<?>)dataFrame.getValue(i);

			List<?> values;

			if(RExpUtil.isFactor(column)){
				RIntegerVector factor = (RIntegerVector)column;

				values = factor.getFactorValues();
			} else

			{
				values = column.getValues();
			}

			VerificationField verificationField = ModelUtil.createVerificationField(FieldName.create(name));

			result.put(verificationField, values);
		}

		return result;
	}

	static
	private class VerificationMap extends LinkedHashMap<VerificationField, List<?>> {

		private Double precision = null;

		private Double zeroThreshold = null;


		public VerificationMap(Double precision, Double zeroThreshold){
			setPrecision(precision);
			setZeroThreshold(zeroThreshold);
		}

		public void putInputData(Map<VerificationField, List<?>> map){
			putAll(map);
		}

		public void putResultData(Map<VerificationField, List<?>> map){
			Double precision = getPrecision();
			Double zeroThreshold = getZeroThreshold();

			Collection<VerificationField> verificationFields = map.keySet();
			for(VerificationField verificationField : verificationFields){
				verificationField
					.setPrecision(precision)
					.setZeroThreshold(zeroThreshold);
			}

			putAll(map);
		}

		public double getPrecision(){
			return this.precision;
		}

		private void setPrecision(double precision){
			this.precision = precision;
		}

		public double getZeroThreshold(){
			return this.zeroThreshold;
		}

		private void setZeroThreshold(double zeroThreshold){
			this.zeroThreshold = zeroThreshold;
		}
	}
}
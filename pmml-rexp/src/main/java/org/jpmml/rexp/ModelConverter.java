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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.VerificationField;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FeatureImportanceMap;
import org.jpmml.converter.ModelEncoder;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.ScalarLabel;
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

	public Model encode(Schema schema){
		Model model = encodeModel(schema);

		if(this instanceof HasFeatureImportances){
			HasFeatureImportances hasFeatureImportances = (HasFeatureImportances)this;

			FeatureImportanceMap featureImportances = hasFeatureImportances.getFeatureImportances(schema);
			if(featureImportances != null && !featureImportances.isEmpty()){
				ModelEncoder encoder = (ModelEncoder)schema.getEncoder();

				Collection<Map.Entry<Feature, Number>> entries = featureImportances.entrySet();
				for(Map.Entry<Feature, Number> entry : entries){
					encoder.addFeatureImportance(model, entry.getKey(), entry.getValue());
				}
			}
		}

		return model;
	}

	@Override
	public PMML encodePMML(RExpEncoder encoder){
		RExp object = getObject();

		RGenericVector verification = null;

		if(object instanceof S4Object){
			S4Object model = (S4Object)object;

			verification = model.getGenericAttribute("verification", false);
		} else

		if(object instanceof RGenericVector){
			RGenericVector model = (RGenericVector)object;

			verification = model.getGenericElement("verification", false);
		}

		encodeSchema(encoder);

		Schema schema = encoder.createSchema();

		Model model = encode(schema);

		verification:
		if(verification != null){
			RDoubleVector precision = verification.getDoubleElement("precision");
			RDoubleVector zeroThreshold = verification.getDoubleElement("zeroThreshold");

			VerificationMap data = new VerificationMap(precision.asScalar(), zeroThreshold.asScalar());

			RGenericVector activeValues = verification.getGenericElement("active_values");
			RGenericVector targetValues = verification.getGenericElement("target_values", false);
			RGenericVector outputValues = verification.getGenericElement("output_values", false);

			if(activeValues != null){
				data.putInputData(encodeActiveValues(activeValues));
			} // End if

			if(targetValues != null && outputValues == null){
				ScalarLabel scalarLabel = (ScalarLabel)schema.getLabel();

				String name = scalarLabel.getName();

				Collection<VerificationField> verificationFields = data.keySet();
				for(Iterator<VerificationField> verificationFieldIt = verificationFields.iterator(); verificationFieldIt.hasNext(); ){
					VerificationField verificationField = verificationFieldIt.next();

					if((verificationField.requireField()).equals(name)){
						verificationFieldIt.remove();
					}
				}

				data.putResultData(encodeTargetValues(targetValues, scalarLabel));
			} else

			if(outputValues != null){
				data.putResultData(encodeOutputValues(outputValues));
			} else

			{
				break verification;
			}

			model.setModelVerification(ModelUtil.createModelVerification(data));
		}

		PMML pmml = encoder.encodePMML(model);

		return pmml;
	}

	protected Map<VerificationField, List<?>> encodeActiveValues(RGenericVector dataFrame){
		return encodeVerificationData(dataFrame);
	}

	protected Map<VerificationField, List<?>> encodeTargetValues(RGenericVector dataFrame, ScalarLabel scalarLabel){
		List<RExp> columns = dataFrame.getValues();
		String name = scalarLabel.getName();

		return encodeVerificationData(columns, Collections.singletonList(name));
	}

	protected Map<VerificationField, List<?>> encodeOutputValues(RGenericVector dataFrame){
		return encodeVerificationData(dataFrame);
	}

	static
	protected Map<VerificationField, List<?>> encodeVerificationData(RGenericVector dataFrame){
		List<RExp> columns = dataFrame.getValues();
		RStringVector columnNames = dataFrame.names();

		return encodeVerificationData(columns, columnNames.getDequotedValues());
	}

	static
	protected Map<VerificationField, List<?>> encodeVerificationData(List<? extends RExp> columns, List<String> names){
		Map<VerificationField, List<?>> result = new LinkedHashMap<>();

		for(int i = 0; i < columns.size(); i++){
			String name = names.get(i);
			RVector<?> column = (RVector<?>)columns.get(i);

			List<?> values;

			if(column instanceof RDoubleVector){
				Function<Double, Double> function = new Function<Double, Double>(){

					@Override
					public Double apply(Double value){

						if(value.isNaN()){
							return null;
						}

						return value;
					}
				};

				values = Lists.transform((List)column.getValues(), function);
			} else

			if(column instanceof RFactorVector){
				RFactorVector factor = (RFactorVector)column;

				values = factor.getFactorValues();
			} else

			{
				values = column.getValues();
			}

			VerificationField verificationField = ModelUtil.createVerificationField(name);

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
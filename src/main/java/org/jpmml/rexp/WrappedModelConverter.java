/*
 * Copyright (c) 2020 Villu Ruusmann
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

import java.util.List;

import com.google.common.collect.Lists;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.Label;
import org.jpmml.converter.Schema;

public class WrappedModelConverter extends FilterModelConverter<RGenericVector, RExp> {

	public WrappedModelConverter(RGenericVector wrappedModel){
		super(wrappedModel);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector wrappedModel = getObject();

		RGenericVector taskDesc = wrappedModel.getGenericElement("task.desc");

		RStringVector type = taskDesc.getStringElement("type");
		RStringVector target = taskDesc.getStringElement("target");

		super.encodeSchema(encoder);

		FieldName targetName = FieldName.create(target.asScalar());

		DataField dataField = encoder.getDataField(targetName);

		switch(type.asScalar()){
			case "regr":
				{
					if(dataField == null){
						dataField = encoder.createDataField(targetName, OpType.CONTINUOUS, DataType.DOUBLE);

						encoder.setLabel(dataField);
					}
				}
				break;
			case "classif":
				{
					RVector<?> classLevels = taskDesc.getVectorElement("class.levels");

					List<?> values = classLevels.getValues();

					if(dataField == null){
						dataField = encoder.createDataField(targetName, OpType.CATEGORICAL, null, values);

						encoder.setLabel(dataField);
					} // End if

					if(!(OpType.CATEGORICAL).equals(dataField.getOpType())){
						dataField = (DataField)encoder.toCategorical(targetName, values);

						encoder.setLabel(dataField);
					} // End if

					if(classLevels.size() == 2){
						RBooleanVector invertLevels = DecorationUtil.getBooleanElement(wrappedModel, "invert_levels");

						if(invertLevels.asScalar()){
							Label label = new CategoricalLabel(dataField.getName(), dataField.getDataType(), Lists.reverse(values));

							encoder.setLabel(label);
						}
					}
				}
				break;
			default:
				throw new IllegalArgumentException();
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		return super.encodeModel(schema);
	}

	@Override
	public ModelConverter<RExp> createConverter(){
		RGenericVector wrappedModel = getObject();

		RExp learnerModel = wrappedModel.getElement("learner.model");

		return (ModelConverter<RExp>)newConverter(learnerModel);
	}
}
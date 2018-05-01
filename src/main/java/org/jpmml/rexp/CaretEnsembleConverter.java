/*
 * Copyright (c) 2018 Villu Ruusmann
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.dmg.pmml.mining.MiningModel;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.mining.MiningModelUtil;

public class CaretEnsembleConverter extends Converter<RGenericVector> {

	public CaretEnsembleConverter(RGenericVector caretEnsemble){
		super(caretEnsemble);
	}

	@Override
	public PMML encodePMML(RExpEncoder encoder){
		RGenericVector caretEnsemble = getObject();

		RGenericVector models = (RGenericVector)caretEnsemble.getValue("models");
		RGenericVector ensModel = (RGenericVector)caretEnsemble.getValue("ens_model");

		RStringVector modelNames = models.names();

		List<Model> segmentationModels = new ArrayList<>();

		Function<Schema, Schema> segmentSchemaFunction = new Function<Schema, Schema>(){

			@Override
			public Schema apply(Schema schema){
				Label label = schema.getLabel();

				if(label instanceof ContinuousLabel){
					return schema.toAnonymousSchema();
				} else

				// XXX: Ideally, the categorical target field should also be anonymized
				if(label instanceof CategoricalLabel){
					return schema;
				} else

				{
					throw new IllegalArgumentException();
				}
			}
		};

		for(int i = 0; i < models.size(); i++){
			RGenericVector model = (RGenericVector)models.getValue(i);

			Conversion conversion = encodeTrainModel(model, segmentSchemaFunction);

			RExpEncoder segmentEncoder = conversion.getEncoder();

			encoder.addFields(segmentEncoder);

			Schema segmentSchema = conversion.getSchema();
			Model segmentModel = conversion.getModel();

			FieldName name = FieldName.create(modelNames.getValue(i));

			OutputField outputField;

			MiningFunction miningFunction = segmentModel.getMiningFunction();
			switch(miningFunction){
				case REGRESSION:
					{
						outputField = ModelUtil.createPredictedField(name, DataType.DOUBLE, OpType.CONTINUOUS)
							.setFinalResult(Boolean.FALSE);
					}
					break;
				case CLASSIFICATION:
					{
						CategoricalLabel categoricalLabel = (CategoricalLabel)segmentSchema.getLabel();
						if(categoricalLabel.size() != 2){
							throw new IllegalArgumentException();
						}

						outputField = ModelUtil.createProbabilityField(name, DataType.DOUBLE, categoricalLabel.getValue(1))
							.setFinalResult(Boolean.FALSE);
					}
					break;
				default:
					throw new IllegalArgumentException();
			}

			Output output = new Output()
				.addOutputFields(outputField);

			segmentModel.setOutput(output);

			segmentationModels.add(segmentModel);
		}

		Conversion conversion = encodeTrainModel(ensModel, null);

		Schema schema = conversion.getSchema();
		Model model = conversion.getModel();

		segmentationModels.add(model);

		MiningModel miningModel = MiningModelUtil.createModelChain(segmentationModels, schema);

		PMML pmml = encoder.encodePMML(miningModel);

		return pmml;
	}

	private Conversion encodeTrainModel(RGenericVector train, Function<Schema, Schema> schemaFunction){
		RExp finalModel = train.getValue("finalModel");

		ModelConverter<?> converter = (ModelConverter<?>)newConverter(finalModel);

		RExpEncoder encoder = new RExpEncoder();

		converter.encodeSchema(encoder);

		Schema schema = encoder.createSchema();

		if(schemaFunction != null){
			schema = schemaFunction.apply(schema);
		}

		Model model = converter.encodeModel(schema);

		return new Conversion(encoder, schema, model);
	}

	static
	private class Conversion {

		private RExpEncoder encoder = null;

		private Schema schema = null;

		private Model model = null;


		private Conversion(RExpEncoder encoder, Schema schema, Model model){
			setEncoder(encoder);
			setSchema(schema);
			setModel(model);
		}

		public RExpEncoder getEncoder(){
			return this.encoder;
		}

		private void setEncoder(RExpEncoder encoder){
			this.encoder = encoder;
		}

		public Schema getSchema(){
			return this.schema;
		}

		private void setSchema(Schema schema){
			this.schema = schema;
		}

		public Model getModel(){
			return this.model;
		}

		private void setModel(Model model){
			this.model = model;
		}
	}
}
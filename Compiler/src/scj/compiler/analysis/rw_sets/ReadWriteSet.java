package scj.compiler.analysis.rw_sets;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.util.intset.OrdinalSet;

public class ReadWriteSet {
	private Map<InstanceKey, Set<IField>> fieldReads;
	private Map<InstanceKey, Set<IField>> fieldWrites;

	static final ReadWriteSet emptySet  = new ReadWriteSet() {
		@Override
		void addFieldRead(InstanceKey instance, IField field) {
			assert false : "shouldn't be called";
		}

		@Override
		void addFieldReads(InstanceKey instance, Set<IField> field) {
			assert false : "shouldn't be called";
		}

		@Override
		void addFieldWrite(InstanceKey instance, IField field) {
			assert false : "shouldn't be called";
		}

		@Override
		void addFieldWrites(InstanceKey instance, Set<IField> field) {
			assert false : "shouldn't be called";
		}

		@Override
		void addAll(ReadWriteSet other) {
			assert false : "shouldn't be called";
		}

		@Override
		public Set<IField> fieldReads(InstanceKey instance) {
			return Collections.emptySet();
		}

		@Override
		public Set<IField> fieldWrites(InstanceKey instance) {
			return Collections.emptySet();
		}
	};

	public Set<Entry<InstanceKey, Set<IField>>> readEntries() {		
		if(fieldReads == null) {
			return Collections.emptySet();
		} else {
			return fieldReads.entrySet();
		}
	}

	public Set<Entry<InstanceKey, Set<IField>>> writeEntries() {
		if(fieldWrites == null) {
			return Collections.emptySet();
		} else {
			return fieldWrites.entrySet();
		}		
	}
	
	private boolean intersects(Set<IField> one, Set<IField> other) {
		for(IField field : one) {
			if(other.contains(field))
				return true;
		}
		return false;
	}
	
	private boolean intersects(Map<InstanceKey, Set<IField>> one, Map<InstanceKey, Set<IField>> other) {
		for(Entry<InstanceKey, Set<IField>> oneEntry : one.entrySet()) {
			Set<IField> otherSet = other.get(oneEntry.getKey());
			if(otherSet != null) {
				if (intersects(oneEntry.getValue(), otherSet)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean readReadConflict(ReadWriteSet other) {
		if(fieldReads == null || other.fieldReads == null)
			return false;
		
		return intersects(fieldReads, other.fieldReads);
	}
	
	public boolean readWriteConflict(ReadWriteSet other) {
		if(fieldReads == null || other.fieldWrites == null)
			return false;
		
		return intersects(fieldReads, other.fieldWrites);
	}
	
	public boolean writeReadConflict(ReadWriteSet other) {
		if(fieldWrites == null || other.fieldReads == null)
			return false;
		
		return intersects(fieldWrites, other.fieldReads);
	}
	
	public boolean writeWriteConflict(ReadWriteSet other) {
		if(fieldWrites == null || other.fieldWrites == null)
			return false;
		
		return intersects(fieldWrites, other.fieldWrites);
	}

	void addAll(ReadWriteSet other) {
		if(other.fieldReads != null) {
			for(Entry<InstanceKey, Set<IField>> entry : other.fieldReads.entrySet()) {
				assert(entry.getValue() != null);
				this.addFieldReads(entry.getKey(), entry.getValue());
			}
		}

		if(other.fieldWrites != null) {
			for(Entry<InstanceKey, Set<IField>> entry : other.fieldWrites.entrySet()) {
				assert(entry.getValue() != null);
				this.addFieldWrites(entry.getKey(), entry.getValue());
			}
		}
	}

	void addFieldReads(InstanceKey instance, Set<IField> fieldSet) {
		if(fieldReads == null) {
			fieldReads = new HashMap<InstanceKey, Set<IField>>();
		}

		Set<IField> fields = fieldReads.get(instance);
		if(fields == null) {
			fields = new HashSet<IField>();
			fieldReads.put(instance, fields);
		}
		fields.addAll(fieldSet);
	}
	
	void addFieldReads(OrdinalSet<InstanceKey> instances, IField field) {
		for(InstanceKey instance : instances) {
			this.addFieldRead(instance, field);
		}
	}

	void addFieldWrites(InstanceKey instance, Set<IField> fieldSet) {
		if(fieldWrites == null) {
			fieldWrites = new HashMap<InstanceKey, Set<IField>>();
		}

		Set<IField> fields = fieldWrites.get(instance);
		if(fields == null) {
			fields = new HashSet<IField>();
			fieldWrites.put(instance, fields);
		}
		fields.addAll(fieldSet);
	}

	void addFieldWrites(OrdinalSet<InstanceKey> instances, IField field) {
		for(InstanceKey instance : instances) {
			this.addFieldWrite(instance, field);
		}
	}
	
	void addFieldRead(InstanceKey instance, IField field) {
		if(fieldReads == null) {
			fieldReads = new HashMap<InstanceKey, Set<IField>>();
		}

		Set<IField> fields = fieldReads.get(instance);
		if(fields == null) {
			fields = new HashSet<IField>();
			fieldReads.put(instance, fields);
		}
		fields.add(field);
	}

	void addFieldWrite(InstanceKey instance, IField field) {
		if(fieldWrites == null) {
			fieldWrites = new HashMap<InstanceKey, Set<IField>>();
		}

		Set<IField> fields = fieldWrites.get(instance);
		if(fields == null) {
			fields = new HashSet<IField>();
			fieldWrites.put(instance, fields);
		}
		fields.add(field);
	}

	public Set<IField> fieldReads(InstanceKey instance) {
		if(fieldReads == null) {
			return Collections.emptySet();
		}

		Set<IField> fields = fieldReads.get(instance);
		if(fields == null) {
			return Collections.emptySet();
		} else {
			return fields;
		}
	}

	public Set<IField> fieldWrites(InstanceKey instance) {
		if(fieldWrites == null) {
			return Collections.emptySet();
		}

		Set<IField> fields = fieldWrites.get(instance);
		if(fields == null) {
			return Collections.emptySet();
		} else {
			return fields;
		}
	}
}

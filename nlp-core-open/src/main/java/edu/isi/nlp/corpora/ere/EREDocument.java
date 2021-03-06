package edu.isi.nlp.corpora.ere;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;

public final class EREDocument {
  private final String docid;
  private final String sourceType;
  private final ImmutableList<EREEntity> entities;
  private final ImmutableList<EREFiller> fillers;
  private final ImmutableList<ERERelation> relations;
  private final ImmutableList<EREEvent> events;
  private final ImmutableMap<EREEntityMention, EREEntity> mentionToEntityMap;

  private EREDocument(
      final String docid,
      final String sourceType,
      final List<EREEntity> entities,
      final List<EREFiller> fillers,
      final List<ERERelation> relations,
      final List<EREEvent> events) {
    this.docid = checkNotNull(docid);
    this.sourceType = checkNotNull(sourceType);
    this.entities = ImmutableList.copyOf(entities);
    this.fillers = ImmutableList.copyOf(fillers);
    this.relations = ImmutableList.copyOf(relations);
    this.events = ImmutableList.copyOf(events);
    this.mentionToEntityMap = buildMentionToEntityMap();
  }

  public String getDocId() {
    return docid;
  }

  public String getSourceType() {
    return sourceType;
  }

  public List<EREEntity> getEntities() {
    return entities;
  }

  public List<EREFiller> getFillers() {
    return fillers;
  }

  public List<ERERelation> getRelations() {
    return relations;
  }

  public List<EREEvent> getEvents() {
    return events;
  }

  public Optional<EREEntity> getEntityContaining(EREEntityMention entityMention) {
    return Optional.fromNullable(mentionToEntityMap.get(entityMention));
  }

  public static Builder builder(final String docid, final String sourceType) {
    return new Builder(docid, sourceType);
  }

  public static class Builder {
    private final String docid;
    private final String sourceType;
    private final List<EREEntity> entities;
    private final List<EREFiller> fillers;
    private final List<ERERelation> relations;
    private final List<EREEvent> events;

    public Builder(final String docid, final String sourceType) {
      this.docid = checkNotNull(docid);
      this.sourceType = checkNotNull(sourceType);
      this.entities = Lists.newArrayList();
      this.fillers = Lists.newArrayList();
      this.relations = Lists.newArrayList();
      this.events = Lists.newArrayList();
    }

    public EREDocument build() {
      return new EREDocument(docid, sourceType, entities, fillers, relations, events);
    }

    public Builder withEntity(EREEntity e) {
      this.entities.add(e);
      return this;
    }

    public Builder withFiller(EREFiller v) {
      this.fillers.add(v);
      return this;
    }

    public Builder withRelation(ERERelation r) {
      this.relations.add(r);
      return this;
    }

    public Builder withEvent(EREEvent e) {
      this.events.add(e);
      return this;
    }
  }

  private ImmutableMap<EREEntityMention, EREEntity> buildMentionToEntityMap() {
    final ImmutableMap.Builder<EREEntityMention, EREEntity> ret = ImmutableMap.builder();

    for (final EREEntity entity : entities) {
      for (final EREEntityMention ereEntityMention : entity.getMentions()) {
        ret.put(ereEntityMention, entity);
      }
    }

    return ret.build();
  }
}

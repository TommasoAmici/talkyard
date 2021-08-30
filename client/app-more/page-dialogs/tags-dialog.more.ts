/*
 * Copyright (C) 2016 Kaj Magnus Lindberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/// <reference path="../more-prelude.more.ts" />
/// <reference path="../react-bootstrap-old/Input.more.ts" />
/// <reference path="../utils/PatternInput.more.ts" />
/// <reference path="../widgets.more.ts" />

//------------------------------------------------------------------------------
   namespace debiki2.pagedialogs {
//------------------------------------------------------------------------------

const r = ReactDOMFactories;
const Modal = rb.Modal;
const ModalHeader = rb.ModalHeader;
const ModalTitle = rb.ModalTitle;
const ModalBody = rb.ModalBody;
const ModalFooter = rb.ModalFooter;
const PatternInput = utils.PatternInput;


let tagsDialog;

interface TagsDiagProps {
  // No props.
}

interface TagsDiagState {
  store?: Store;
  isOpen?: Bo;
  isLoading?: Bo;
  tagTypes: TagType[],
  allTags; // old
  post?: Post;
  curTags: Tag[],
  tags: St[], // old
}


export function openTagsDialog(store: Store, post: Post) {
  if (!tagsDialog) {
    tagsDialog = ReactDOM.render(TagsDialog(), utils.makeMountNode());
  }
  tagsDialog.open(store, post);
}


const TagsDialog = createComponent({
  displayName: 'TagsDialog',

  getInitialState: function () {
    return {
      tags: [],
      tagTypes: [],
      allTags: [],
    } as TagsDiagState;
  },

  componentWillUnmount: function() {
    this.isGone = true;
  },

  open: function(store, post: Post) {
    const newState: Partial<TagsDiagState> = { isOpen: true, isLoading: true };
    this.setState(newState);
    Server.listTagTypes(ThingType.Pats, '', (tagTypes: TagType[]) => {
      if (this.isGone) return;
      const newState: Partial<TagsDiagState> = { tagTypes };
      this.setState(newState);
    });
    // old
    Server.loadAllTags((tags) => {
      if (this.isGone) return;
      this.setState({ allTags: tags });
    });
    Server.loadEditorAndMoreBundles(() => {
      if (this.isGone || !this.state.isOpen) return;
      const newState: Partial<TagsDiagState> = {
        isLoading: false,
        store: store,
        post: post,
        tags: _.clone(post.tags),  // old
        curTags: _.clone(post.tags2),
      };
      this.setState(newState);
    });
  },

  close: function() {
    this.setState({ isOpen: false, store: null, post: null, tags: null });
  },

  // old
  __onSelectChange: function(labelsAndValues: any) {
    labelsAndValues = labelsAndValues || []; // is null if the clear-all [x] button pressed
    this.setState({ tags: labelsAndValues.map(labelValue => labelValue.value) });
  },

  onSelectChange: function(labelsAndValues: any) {
    const state: TagsDiagState = this.state;
    labelsAndValues = labelsAndValues || []; // is null if the clear-all [x] button pressed
    const curTags = state.curTags;
    const newCurTags = [];
    for (const labVal of labelsAndValues) {
      const tagOrTagTypeId = parseInt(labVal.value);
      const isTagId = tagOrTagTypeId > 0;
      const tagId = isTagId && tagOrTagTypeId;
      const tagTypeId = !isTagId && -tagOrTagTypeId;  // undo negate_tagtype_id
      const origTag: Tag | U =
              _.find(state.post.tags2, t => t.id === tagId || t.tagTypeId === tagTypeId);
      let newTag: Tag | U;
      if (!origTag) {
        // @ifdef DEBUG
        dieIf(!tagTypeId, 'TyE2F0MW25')
        // @endif
        newTag = tagTypeId && {
          // or just -1, -2, -3, -4? But then how do we know if it's a tag or a tag type
          id: 0, // -tagTypeId,  // 0 // what id? if < 0, will think it's a tag type again, maybe ok?
          tagTypeId,
          onPostId: state.post.uniqueId,
        }
      }
      /*
      if (isTagId) {
        const tagId = tagOrTagTypeId;
        const tag: Tag | U = _.find(state.post.tags2,
                t => t.id === tagId && (t.id > 0 || t.tagTypeId === -tagOrTagTypeId));
        // @ifdef DEBUG
        dieIf(!tag, 'TyE2F0MW25')
        // @endif
        newCurTags.push(tag);
      }
      /*
      else if (tagOrTagTypeId === 0) {
        // ?
      } * /
      else {
        const tagTypeId = -tagOrTagTypeId;  // undo negate_tagtype_id
        const tagType: TagType | U = _.find(state.tagTypes, tt => tt.id === tagTypeId);
        // @ifdef DEBUG
        dieIf(!tagType, 'TyE7J0MW27')
        // @endif
        const newTag: Tag = {
          // or just -1, -2, -3, -4? But then how do we know if it's a tag or a tag type
          id: -tagTypeId,  // 0 // what id? if < 0, will think it's a tag type again, maybe ok?
          tagTypeId,
          onPostId: state.post.uniqueId,
        }
        newCurTags.push(newTag);
      }*/
      const origOrNewTag = origTag || newTag;
      if (origOrNewTag) {
        newCurTags.push(origOrNewTag);
      }
    }
    this.setState({ tags: newCurTags }); // labelsAndValues.map(labelValue => labelValue.value) });
    //this.setState({ tags: labelsAndValues.map(labelValue => labelValue.value) });
  },

  setCanAddTag: function(canAddTag: boolean) {
    this.setState({ canAddTag: canAddTag });
  },

  createAndAddTag: function() {
    // [redux] modifying state in place
    let tags = this.state.tags;
    const newTag = this.refs.newTagInput.getValue();
    tags.push(newTag);
    tags = _.uniq(tags);
    this.setState({ tags: tags });
  },

  save: function() {
    Server.addRemovePostTags(this.state.post.uniqueId, this.state.tags, () => {
      if (this.isGone) return;
      this.close();
    });
  },

  render: function () {
    const state: TagsDiagState = this.state;
    let title: St | U;
    let content: RElm | U;

    if (this.state.isLoading)
      return r.p({}, "Loading...");

    if (!this.state.isOpen) {
      // Nothing.
    }
    else {
      const post: Post = state.post;
      dieIf(!post, 'EsE4GK0IF2');
      title = post.nr === BodyNr ? "Page tags" : "Post tags";   // I18N tags, here and below
      content =
        r.div({ className: 'esTsD_CreateTs' },
          rb.ReactSelect({ multi: true,
                value: makeTagLabelValues(state.curTags, state.tagTypes),
                                            //makeLabelValues(state.tags),
            className: 'esTsD_TsS', placeholder: "Select tags",
            options: makeTagTypeLabelValues( // makeLabelValues(
                  state.tagTypes), onChange: this.onSelectChange }),
                  //this.state.allTags), onChange: this.onSelectChange }),
          r.div({},
            PatternInput({ label: "Create tag:", ref: 'newTagInput', placeholder: "tag-name",
              onChangeValueOk: (value, ok) => this.setCanAddTag(ok),
              help: "Type a new tag name.",
              notRegex: /\s/, notMessage: "No spaces",
              // Sync with Scala and database. [7JES4R3]
              //notRegexTwo: /[,;\|\?!\*'"]/
              notRegexTwo: /[!"#$%&'()*+,\/;<=>?@[\]^`{|}\\]/,
              notMessageTwo: "No weird chars like ',&?*' please",
            }),
            Button({ onClick: this.createAndAddTag, disabled: !this.state.canAddTag },
              "Create and add tag")));
    }

    return (
      Modal({ show: this.state.isOpen, onHide: this.close, dialogClassName: 'esTsD' },
        ModalHeader({}, ModalTitle({}, title)),
        ModalBody({}, content),
        ModalFooter({},
          PrimaryButton({ onClick: this.save }, "Save"),
          Button({ onClick: this.close }, "Cancel"))));
  }
});


function makeTagLabelValues(tags: Tag[], tagTypes: TagType[]) {
  return tags.map(tag => {
    const tagType = _.find(tagTypes, it => it.id === tag.tagTypeId);
    return {
      label: tagType ? tagType.dispName : `tagtypeid:${tag.tagTypeId}`,
      value: tag.id,
    };
  });
}


function makeTagTypeLabelValues(tagTypes: TagType[]) {
  return tagTypes.map(tagType => {
    // Minus means it's a tag type id, not a tag id, negate_tagtype_id.
    return { label: tagType.dispName, value: -tagType.id };
  });
}


// old
function makeLabelValues(tags: string[]) {
  return tags.map(tag => {
    return { label: tag, value: tag };
  });
}

//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 fo=r list

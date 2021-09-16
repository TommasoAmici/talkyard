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
  editorLoaded?: Bo;
  tagTypes?: TagType[],
  //allTags; // old
  //post?: Post;
  forPost?: Post;
  forPat?: Pat;
  curTags: Tag[],
  tags: St[], // old
  canAddTag?: Bo;
}


export function openTagsDialog(ps: { forPost?: Post, forPat?: Pat, store: Store }) {
//export function openTagsDialog(store: Store, post: Post) {
  if (!tagsDialog) {
    tagsDialog = ReactDOM.render(TagsDialog(), utils.makeMountNode());
  }
  tagsDialog.open(ps); // store, post);
}


const TagsDialog = createComponent({
  displayName: 'TagsDialog',

  getInitialState: function () {
    return {};
  },

  componentWillUnmount: function() {
    this.isGone = true;
  },

  open: function(ps: { forPost?: Post, forPat?: Pat, store: Store }) { // store: Store, post: Post) {
    dieIf(!!ps.forPat === !!ps.forPost, 'TyE602MWEG5');
    const newState: Partial<TagsDiagState> = {
      ...ps,  // was: store,
      curTags: _.clone(ps.forPost?.tags2 || ps.forPat?.pubTags),
    };
    this.setState(newState);

    const listWhatTagTypes = ps.forPost ? ThingType.Posts : ThingType.Pats;

    Server.listTagTypes(listWhatTagTypes, '', (tagTypes: TagType[]) => {
      if (this.isGone) return;
      const newState: Partial<TagsDiagState> = {
        tagTypes,
      };
      this.setState(newState);
    });
    /* old
    Server.loadAllTags((tags) => {
      if (this.isGone) return;
      this.setState({ allTags: tags });
    }); */
    const curState: TagsDiagState = this.state;
    if (!curState.editorLoaded) Server.loadEditorAndMoreBundles(() => {
      if (this.isGone) return;
      const newState: Partial<TagsDiagState> = {
        editorLoaded: true,
        //post: post,
        //tags: _.clone(post.tags),  // old
        //curTags: _.clone(ps.forPost?.tags2 || ps.forPat?.pubTags),
      };
      this.setState(newState);
    });
  },

  close: function() {
    const newState: Partial<TagsDiagState> = {
            isOpen: false, store: null, forPost: null, forPat: null,
            curTags: null, tags: null, tagTypes: null };
    this.setState(newState);
  },

  /* old
  __onSelectChange: function(labelsAndValues: any) {
    labelsAndValues = labelsAndValues || []; // is null if the clear-all [x] button pressed
    this.setState({ tags: labelsAndValues.map(labelValue => labelValue.value) });
  }, */

  onSelectChange: function(labelsAndValues: any) {
    const state: TagsDiagState = this.state;
    labelsAndValues = labelsAndValues || []; // is null if the clear-all [x] button pressed
    const origTags = state.forPost?.tags2 || state.forPat?.pubTags;
    const curTags = state.curTags;
    const newCurTags = [];
    for (const labVal of labelsAndValues) {
      const tagOrTagTypeId = parseInt(labVal.value);
      const isTagId = tagOrTagTypeId > 0;
      const tagId = isTagId && tagOrTagTypeId;
      const tagTypeId = !isTagId && -tagOrTagTypeId;  // undo negate_tagtype_id
      const origTag: Tag | U =
              _.find(origTags, t => t.id === tagId || t.tagTypeId === tagTypeId);
              //_.find(state.post.tags2, t => t.id === tagId || t.tagTypeId === tagTypeId);

      let newTag: Tag | U;
      if (!origTag) {
        // @ifdef DEBUG
        dieIf(!tagTypeId, 'TyE2F0MW25')
        // @endif
        newTag = tagTypeId && {
          // or just -1, -2, -3, -4? But then how do we know if it's a tag or a tag type
          id: 0, // -tagTypeId,  // 0 // what id? if < 0, will think it's a tag type again, maybe ok?
          tagTypeId,
          onPostId: state.forPost?.uniqueId,
          onPatId: state.forPat?.id,
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
    const newState: Partial<TagsDiagState> = { curTags: newCurTags };
    this.setState(newState); // labelsAndValues.map(labelValue => labelValue.value) });
    //this.setState({ tags: labelsAndValues.map(labelValue => labelValue.value) });
  },

  setCanAddTag: function(canAddTag: boolean) {
    this.setState({ canAddTag: canAddTag });
  },

  createAndAddTag: function() {
    // [redux] modifying state in place
    const state: TagsDiagState = this.state;
    const newTagType: TagType = {
      id: No.TagTypeId as TagTypeId,
      dispName: this.refs.newTagInput.getValue(),
      canTagWhat: ThingType.Posts,
    };
    Server.createTagType(newTagType, (tagTypeWitId: TagType) => {
      if (this.isGone) return;
      const state2: TagsDiagState = this.state;
      const differentPat = state.forPat?.id !== state2.forPat?.id;
      const differentPost = state.forPost?.uniqueId !== state2.forPost?.uniqueId;
      if (differentPat || differentPost) return;
      const newTag: Tag = {
        id: No.TagId as TagId,
        tagTypeId: tagTypeWitId.id,
        onPatId: state.forPat?.id,
        onPostId: state.forPost?.uniqueId,
      }
      this.setState({
        curTags: [...state.curTags, newTag],
        tagTypes: [...state.tagTypes, tagTypeWitId],
      });
      //this.refs.newTagInput.clear();  — not
    });
    /*
    let tags = this.state.tags;
    const newTag = this.refs.newTagInput.getValue();
    tags.push(newTag);
    tags = _.uniq(tags);
    this.setState({ tags: tags });
    */
  },

  save: function() {
    const state: TagsDiagState = this.state;
    // We know if a tag is new, because it then has no tag id.
    const tagsAdded: Tag[] = _.filter(state.curTags, t => t.id === No.TagId);
    const tagsBefore = state.forPost?.tags2 || state.forPat?.pubTags || [];
    const tagsRemoved: Tag[] = [];
    for (const tagBef of tagsBefore) {
      const tagNow = _.find(state.curTags, t => t.id === tagBef.id);
      if (!tagNow) {
        tagsRemoved.push(tagBef);
      }
    }
    Server.addRemoveTags({ add: tagsAdded, remove: tagsRemoved }, () => {
      if (this.isGone) return;
      this.close();
    });
  },

  render: function () {
    const state: TagsDiagState = this.state;
    let title: St | U;
    let content: RElm | U;

    if (!state.curTags) {
      // Not open.
    }
    else if (!state.editorLoaded || !state.tagTypes) {
      content = r.p({}, t.Loading);
    }
    else {
      // I18N tags dialog, here and below.
      if (state.forPost) {
        title = state.forPost.nr === BodyNr ? "Page tags" : "Post tags";
      }
      else if (state.forPat) {
        title = (state.forPat.isGroup ? "Group Member" : "User") + " Badges";
      }
      else {
        die('TyE4GK0IF2');
      }

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
              // Bad chars, sync w Scala and database:  [7JES4R3-2]
              // """.*[^\p{Alnum} !?&%_.:=/~+*-].*""".r
              //notRegexTwo: /[,;\|\?!\*'"]/
              //notRegexTwo: /[!"#$%&'()*+,\/;<=>?@[\]^`{|}\\]/,
              notRegexTwo: /["#$'(),;<>@[\]^`{|}\\]/,
              notMessageTwo: "No weird chars like ',#[{(' please",
            }),
            Button({ onClick: this.createAndAddTag, disabled: !state.canAddTag },
              "Create and add tag"),
            r.span({ className: 'help-block' },
              "(You need to click Save too, to the right.)")));
    }

    return (
      Modal({ show: !!content, onHide: this.close, dialogClassName: 'esTsD' },
        ModalHeader({}, ModalTitle({}, title)),
        ModalBody({}, content),
        ModalFooter({},
          PrimaryButton({ onClick: this.save }, "Save"),
          Button({ onClick: this.close }, "Cancel"))));
  }
});


function makeTagLabelValues(tags: Tag[], tagTypes: TagType[]) {
  return tags.map((tag: Tag) => {
    const idOrTagTypeId = tag.id;
    // Hack, to play nice with ReactSelect: For new tags, the id is 0 (they don't yet
    // have any id), and they're instead identified by tag type, which
    // we store in { value }, negative to know it's not a tag id, but a tag type id.
    const value = tag.id || -tag.tagTypeId;  // negate_tagtype_id
    //const tagId: TagId | false = tag.id > 0 && tag.id;
    //const tagTypeId: TagId | false = tag.id < 0 && -tag.id;
    const tagType = _.find(tagTypes, tagType => tagType.id === tag.tagTypeId);
    return {
      label: tagType ? tagType.dispName : `tagTypeId=${tag.tagTypeId}`,
      value,
    };
  });
}


function makeTagTypeLabelValues(tagTypes: TagType[]) {
  return tagTypes.map((tagType: TagType) => {
    // Minus means it's a tag type id, not a tag id, so, in onSelectChange(), we know
    // the { label, value } refers to a newly added tag, with tag type = -value.
    return { label: tagType.dispName, value: -tagType.id };  // negate_tagtype_id
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
